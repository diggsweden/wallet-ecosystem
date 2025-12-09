// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class WalletClientGatewayTest {

  private final WalletClientGatewayClient walletClientGateway = new WalletClientGatewayClient();
  private static final String KEY_ID = "123";
  private static ECKey ecKey;
  private static String oidcSession;
  private static String accountId;
  private static String session;

  @BeforeAll
  static void beforeAll() throws Exception {
    ecKey = generateKey();
  }

  @Test
  void isHealthy() {
    walletClientGateway.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  @Order(1)
  void loginOidc() throws Exception {
    oidcSession = oidcLogin();
    assertNotNull(oidcSession);
    System.out.println("OIDC Session: " + oidcSession);
  }

  @Test
  @Order(2)
  void createAccount() throws Exception {
    accountId = walletClientGateway.createAccount(
        """
                {
                  "emailAdress": "test@hej.se",
                  "telephoneNumber": "070123123123",
                  "publicKey": %s
                }
            """.formatted(ecKey.toPublicJWK().toJSONString()),
        oidcSession);
    assertNotNull(accountId);
    System.out.println("Account id: " + accountId);
  }

  @Test
  @Order(3)
  void loginChallengeResponse() throws Exception {
    var nonce = walletClientGateway.initChallenge(accountId, KEY_ID);
    var signedJwt = createSignedJwt(ecKey, nonce);
    session = walletClientGateway.respondToChallenge(signedJwt);

    assertNotNull(session);
    System.out.println("Challenge response session: " + session);
  }


  @Test
  void createsAndGetAttributeAttestation()
      throws Exception {
    var postBody = """
        {
        "hsmId": "cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49",
        "wuaId": "790acda4-3dec-4d93-8efe-71375109d30e",
        "attestationData": "string"
        }""";
    var createdId = walletClientGateway.createAttributeAttestation(session, postBody);

    UUID.fromString(createdId); // verify it's a valid uuid

    walletClientGateway.getAttributeAttestation(session, createdId)
        .then()
        .assertThat().statusCode(200)
        .and().body("hsmId", equalTo("cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49"));
  }

  @Test
  void createsWalletUnitAttestation() throws Exception {
    var postBody = """
        {
          "walletId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "jwk": {
            "kty": "EC",
            "crv": "P-256",
            "x": "1fH0eqXgMMwCIafNaDc1axdCjLlw7zpTLvLWjpPvhEc",
            "y": "5qOejJs7BK-jLingaUTEhBrzP_YPyHfptS5yWE98I40"
          }
        }""";
    walletClientGateway.createWalletUnitAttestation(session, postBody)
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  private static ECKey generateKey() throws Exception {
    return new ECKeyGenerator(Curve.P_256)
        .keyID(KEY_ID)
        .algorithm(Algorithm.NONE)
        .keyUse(KeyUse.SIGNATURE)
        .generate();
  }

  private String oidcLogin() {
    CookieFilter cookies = new CookieFilter();

    var redirectToKeycloak = given()
        .filter(cookies)
        .when().contentType(ContentType.JSON).body("bogusbody")
        .post("https://localhost/wallet-client-gateway/oidc/accounts/v1")
        .then()
        .assertThat()
        .statusCode(302).and()
        .extract()
        .response();

    var redirectToKeycloakLogin = given()
        .filter(cookies)
        .redirects().follow(false)
        .get(redirectToKeycloak.getHeader("Location"))
        .then()
        .extract()
        .response();

    var keycloakLoginPage = given()
        .filter(cookies)
        .redirects().follow(true)
        .log().all()
        .urlEncodingEnabled(false)
        .get(redirectToKeycloakLogin.getHeader("Location"));

    var loginAction =
        keycloakLoginPage.htmlPath().getString("**.find { it.@id=='kc-form-login' }.@action");
    var loginResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .formParam("username", "test1")
        .formParam("password", "test1")
        .log()
        .all()
        .post(loginAction);

    var applicationResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .urlEncodingEnabled(false)
        .log()
        .all()
        .get(loginResponse.getHeader("Location"));

    return applicationResponse.cookie("SESSION");
  }

  private static String createSignedJwt(ECKey ecJwk, String nonce)
      throws JOSEException {
    var claims = new JWTClaimsSet.Builder()
        .claim("nonce", nonce)
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).build();
    var signedJwt = new SignedJWT(header, claims);

    signedJwt.sign(new ECDSASigner(ecJwk));

    return signedJwt.serialize();
  }

}
