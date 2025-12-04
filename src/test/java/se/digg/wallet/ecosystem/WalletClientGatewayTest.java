// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.SSLConfig.sslConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class WalletClientGatewayTest {



  static RequestSpecification given() {
    return RestAssured.given()
        .config(
            RestAssured.config()
                .sslConfig(sslConfig().relaxedHTTPSValidation())
                .logConfig(logConfig().enableLoggingOfRequestAndResponseIfValidationFails())
                .encoderConfig(
                    encoderConfig()
                        .encodeContentTypeAs("application/jwt", ContentType.TEXT)
                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)));
  }

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
    given()
        .when()
        .get("https://localhost/wallet-client-gateway/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  @Order(1)
  void loginOidc() throws Exception {
    oidcSession = oidcLogin(ecKey);
    assertNotNull(oidcSession);
  }

  @Test
  @Order(2)
  void createAccount() throws Exception {
    accountId = given()
        .when()
        .contentType(ContentType.JSON)
        .cookie("SESSION", oidcSession)
        .body("""
            {
              "emailAdress": "test@hej.se",
              "telephoneNumber": "070123123123",
              "publicKey": %s
                }""".formatted(ecKey.toPublicJWK().toJSONString()))
        .post("https://localhost/wallet-client-gateway/oidc/accounts/v1")
        .then()
        .assertThat()
        .statusCode(201)
        .and()
        .extract()
        .response()
        .jsonPath()
        .getString("accountId");
    assertNotNull(accountId);
  }

  @Test
  @Order(3)
  void loginChallengeResponse() throws Exception {
    var nonce = initChallenge(accountId);
    var signedJwt = createSignedJwt(ecKey, nonce, accountId);
    session = respondToChallenge(signedJwt);

    assertNotNull(session);
  }


  @Test
  void createsAndGetAttributeAttestation()
      throws Exception {
    var createdId = given().when().contentType(ContentType.JSON).body("""
        {
        "hsmId": "cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49",
        "wuaId": "790acda4-3dec-4d93-8efe-71375109d30e",
        "attestationData": "string"
        }""")
        .header("Session", session)
        .post("https://localhost/wallet-client-gateway/attribute-attestations")
        .then()
        .assertThat().statusCode(201).and()
        .extract()
        .body()
        .jsonPath()
        .getString("id");

    UUID.fromString(createdId); // verify it's a valid uuid

    given()
        .when()
        .header("Session", session)
        .get(
            "https://localhost/wallet-client-gateway/attribute-attestations/%s"
                .formatted(createdId))
        .then()
        .assertThat().statusCode(200)
        .and().body("hsmId", equalTo("cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49"));
  }

  @Test
  void createsWalletUnitAttestation() throws Exception {
    given()
        .when()
        .contentType(ContentType.JSON)
        .body("""
            {
              "walletId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "jwk": {
                "kty": "EC",
                "crv": "P-256",
                "x": "1fH0eqXgMMwCIafNaDc1axdCjLlw7zpTLvLWjpPvhEc",
                "y": "5qOejJs7BK-jLingaUTEhBrzP_YPyHfptS5yWE98I40"
              }
            }""")
        .header("X-API-KEY", "apikey")
        .post("https://localhost/wallet-client-gateway/wua")
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

  private String oidcLogin(ECKey ecKey) { // TODO no nneed for key
    CookieFilter cookies = new CookieFilter();

    var springSession = given()
        .filter(cookies)
        .when().contentType(ContentType.JSON).body("""
            {
              "personalIdentityNumber": "197001011234",
              "emailAdress": "test@hej.se",
              "telephoneNumber": "070123123123",
              "publicKey": %s
                }""".formatted(ecKey.toPublicJWK().toJSONString()))
        .post("https://localhost/wallet-client-gateway/oidc/accounts/v1")
        .then()
        .assertThat()
        .statusCode(302).and()
        .extract()
        .response();

    var redirectUrl = springSession.getHeader("Location");

    var myProviderResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .get(redirectUrl)
        .then()
        .extract()
        .response();

    var redirectUrltoKeyCloak = myProviderResponse.getHeader("Location");
    var keycloakLoginPage = given()
        .filter(cookies)
        .redirects().follow(true)
        .log().all()
        .urlEncodingEnabled(false)
        .get(redirectUrltoKeyCloak);

    String loginAction =
        keycloakLoginPage.htmlPath().getString("**.find { it.@id=='kc-form-login' }.@action");
    var loginResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .formParam("username", "test1")
        .formParam("password", "test1")
        .log()
        .all()
        .post(loginAction);

    String backToApp = loginResponse.getHeader("Location");
    var applicationResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .urlEncodingEnabled(false)
        .log()
        .all()
        .get(backToApp);

    System.out.println("We have some session ID: " + applicationResponse.cookies());
    return applicationResponse.cookie("SESSION");
  }

  private String initChallenge(String accountId) {
    return given()
        .get(
            "https://localhost/wallet-client-gateway/public/auth/session/challenge?accountId=%s&keyId=%s"
                .formatted(accountId, "123"))
        .then()
        .assertThat().statusCode(200).and()
        .extract()
        .body()
        .jsonPath()
        .getString("nonce");
  }

  private String respondToChallenge(String signedJwt) {
    return given()
        .when().contentType(ContentType.JSON).body("""
            {"signedJwt": "%s"}
            """.formatted(signedJwt))
        .post("https://localhost/wallet-client-gateway/public/auth/session/response")
        .then()
        .assertThat().statusCode(200)
        .extract()
        .response()
        .getHeaders()
        .getValue("session");
  }

  private static String createSignedJwt(ECKey ecJwk, String nonce, String accountId)
      throws JOSEException {
    JWSSigner signer = new ECDSASigner(ecJwk);
    JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
        .claim("accountId", accountId)
        .claim("nonce", nonce)
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
    SignedJWT signedJwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).build(),
        claimsSet);

    signedJwt.sign(signer);

    return signedJwt.serialize();
  }

}
