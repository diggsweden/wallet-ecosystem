// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
public class WalletClientGatewayTest {

  public static final String API_KEY = Optional.ofNullable(System.getenv(
      "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_API_KEY")).orElse("apikey");
  private static final WalletClientGatewayClient walletClientGateway =
      new WalletClientGatewayClient();
  private static final String KEY_ID = "123";
  private static String session;

  @BeforeAll
  static void beforeAll() throws Exception {
    var ecKey = generateKey();

    var accountId = createAccountByApiKey(ecKey);

    assertAll(
        () -> assertNotNull(accountId),
        () -> assertFalse(accountId.isEmpty()));

    var nonce = walletClientGateway.initChallenge(accountId, KEY_ID);
    var signedJwt = createSignedJwt(ecKey, nonce);
    session = walletClientGateway.respondToChallenge(signedJwt);
  }

  @Test
  void appActuatorHealth_status_shouldReturnUP() {
    walletClientGateway.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  @Deprecated
  void createAccount_usingOidc_shouldReturnAccountId() throws Exception {
    var accountId = createAccountByOidc(generateKey());

    assertAll(
        () -> assertNotNull(accountId),
        () -> assertFalse(accountId.isEmpty()));
  }

  @Test
  @Deprecated
  void createAccountAndLoginWithChallenge_usingOidc_shouldSucceed() throws Exception {
    var ecKey = generateKey();
    // step one, create Account
    var accountId = createAccountByOidc(ecKey);

    // step two, create challenge
    // use nonce to created signedJwt
    var nonce = walletClientGateway.initChallenge(accountId, KEY_ID);
    var signedJwt = createSignedJwt(ecKey, nonce);

    // step three, post response and expect success
    assertDoesNotThrow(() -> walletClientGateway.respondToChallenge(signedJwt));
  }

  @Test
  void createsAndGetAttributeAttestation() {
    var postBody = """
        {
        "hsmId": "cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49",
        "wuaId": "790acda4-3dec-4d93-8efe-71375109d30e",
        "attestationData": "string"
        }""";
    var createdId = walletClientGateway.createAttributeAttestation(session, postBody);

    walletClientGateway.getAttributeAttestation(session, createdId)
        .then()
        .assertThat().statusCode(200)
        .and().body("hsmId", equalTo("cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49"));
  }

  @Test
  void createsWalletUnitAttestation() {
    walletClientGateway.createWalletUnitAttestation(session, null)
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonce"})
  @NullSource
  void createsWalletUnitAttestationWithAndWithoutNonce(String nonce) {
    walletClientGateway.createWalletUnitAttestation(session, nonce)
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  @Test
  void createsWalletUnitAttestation_withEmptyNonce_shouldGiveBadRequest() {
    String emptyNonce = "";
    walletClientGateway.createWalletUnitAttestation(session, emptyNonce)
        .then()
        .assertThat().statusCode(400);
  }

  private static ECKey generateKey() throws Exception {
    return new ECKeyGenerator(Curve.P_256)
        .keyID(KEY_ID)
        .algorithm(Algorithm.NONE)
        .keyUse(KeyUse.SIGNATURE)
        .generate();
  }

  private static String createAccountByApiKey(ECKey ecKey) {
    var accountRequestBody = """
        {
          "personalIdentityNumber": "197001011234",
          "emailAdress": "test@hej.se",
          "telephoneNumber": "070123123123",
          "publicKey": %s
        }""".formatted(ecKey.toPublicJWK().toJSONString());
    return walletClientGateway.createAccountByApiKey(
        accountRequestBody, API_KEY, "accounts");
  }

  @Deprecated
  private String createAccountByOidc(ECKey key) {
    var oidcSession = oidcLogin();

    assertAll(
        () -> assertNotNull(oidcSession),
        () -> assertFalse(oidcSession.isEmpty()));

    return walletClientGateway.createAccountByOidc("""
            {
              "emailAdress": "test@hej.se",
              "telephoneNumber": "070123123123",
              "publicKey": %s
            }
        """.formatted(key.toPublicJWK().toJSONString()), oidcSession);
  }

  private String oidcLogin() {
    CookieFilter cookies = new CookieFilter();

    var redirectToKeycloak = given()
        .filter(cookies)
        .when().contentType(ContentType.JSON)
        .post(ServiceIdentifier.WALLET_CLIENT_GATEWAY.getResourceRoot().resolve("oidc/accounts/v1"))
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
        .urlEncodingEnabled(false)
        .get(redirectToKeycloakLogin.getHeader("Location"));

    var loginAction =
        keycloakLoginPage.htmlPath().getString("**.find { it.@id=='kc-form-login' }.@action");
    var loginResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .formParam("username", "test1")
        .formParam("password", "test1")
        .post(loginAction);

    var applicationResponse = given()
        .filter(cookies)
        .redirects().follow(false)
        .urlEncodingEnabled(false)
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
