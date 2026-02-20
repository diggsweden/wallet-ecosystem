// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
public class WalletClientGatewayTest {

  public static final List<String> ACCOUNTS_PATH = List.of("accounts", "accounts/v1");
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
  void appActuatorHealth_status_shouldReturnUP() {
    walletClientGateway.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  @Order(1)
  void login_usingOidcAuth_shouldReturnSession() throws Exception {
    oidcSession = oidcLogin();

    assertAll(
        () -> assertNotNull(oidcSession),
        () -> assertFalse(oidcSession.isEmpty()));
  }

  @Test
  @Order(2)
  void createAccount_usingOidcAuth_shouldReturnAccountId() throws Exception {
    accountId = walletClientGateway.createAccountByOidc(
        """
                {
                  "emailAdress": "test@hej.se",
                  "telephoneNumber": "070123123123",
                  "publicKey": %s
                }
            """.formatted(ecKey.toPublicJWK().toJSONString()),
        oidcSession);

    assertAll(
        () -> assertNotNull(accountId),
        () -> assertFalse(accountId.isEmpty()));
  }

  @ParameterizedTest
  @FieldSource("ACCOUNTS_PATH")
  @Order(3)
  void createAccount_usingApiKeyAuth_shouldReturnAccountId(String path) throws Exception {
    var ecKey = generateKey();
    var accountRequestBody = """
        {
          "personalIdentityNumber": "197001011234",
          "emailAdress": "test@hej.se",
          "telephoneNumber": "070123123123",
          "publicKey": %s
        }""".formatted(ecKey.toPublicJWK().toJSONString());
    var apiKey = "apikey";
    var accountId = walletClientGateway.createAccountByApiKey(accountRequestBody, apiKey, path);

    assertAll(
        () -> assertNotNull(accountId),
        () -> assertFalse(accountId.isEmpty()));
  }

  @Test
  @Order(4)
  void loginChallenge_signedJwt_shouldReturnSessionInBody() throws Exception {
    var nonce = walletClientGateway.initChallenge(accountId, KEY_ID);
    var signedJwt = createSignedJwt(ecKey, nonce);
    var response = walletClientGateway.respondToChallenge(signedJwt);
    session = response.jsonPath().get("sessionId");

    assertAll(
        () -> assertNotNull(session),
        () -> assertFalse(session.isEmpty()));
  }

  @Test
  @Order(5)
  void loginChallenge_signedJwt_shouldReturnSessionInHeader_deprecated() throws Exception {
    var nonce = walletClientGateway.initChallenge(accountId, KEY_ID);
    var signedJwt = createSignedJwt(ecKey, nonce);
    var response = walletClientGateway.respondToChallenge(signedJwt);
    session = response.response().getHeaders().getValue("session");

    assertAll(
        () -> assertNotNull(session),
        () -> assertFalse(session.isEmpty()));
  }

  @Test
  void createAccountAndLoginWithChallenge_usingApiKey_shouldReturnSessionId() throws Exception {
    var ecKey = generateKey();
    // step one, create Account
    var accountRequestBody = """
        {
          "personalIdentityNumber": "197001011234",
          "emailAdress": "test@hej.se",
          "telephoneNumber": "070123123123",
          "publicKey": %s
        }""".formatted(ecKey.toPublicJWK().toJSONString());
    var apiKey = "apikey";
    var accountId =
        walletClientGateway.createAccountByApiKey(accountRequestBody, apiKey, "accounts");

    // step two, create challenge
    // use nonce to created signedJwt
    var nonce = walletClientGateway.initChallenge(accountId, KEY_ID);
    var signedJwt = createSignedJwt(ecKey, nonce);

    // step three, post response and expect a session in the header
    var sessionResponse = walletClientGateway.respondToChallenge(signedJwt).response();

    // Assert session response
    var actualBodySessionId = sessionResponse
        .body()
        .jsonPath()
        .getString("sessionId");

    var actualHeaderSessionId = sessionResponse
        .getHeaders()
        .getValue("Session");

    assertAll(
        () -> assertNotNull(actualBodySessionId),
        () -> assertFalse(actualBodySessionId.isEmpty()),
        () -> assertNotNull(actualHeaderSessionId),
        () -> assertFalse(actualHeaderSessionId.isEmpty()),
        () -> assertEquals(actualBodySessionId, actualHeaderSessionId));
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
    walletClientGateway.createWalletUnitAttestation(session, null, "wua")
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonce"})
  @NullSource
  void createsWalletUnitAttestationWithAndWithoutNonce(String nonce) throws Exception {
    walletClientGateway.createWalletUnitAttestation(session, nonce, "wua")
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  @Test
  void createsWalletUnitAttestation_withEmptyNonce_shouldGiveBadRequest()
      throws Exception {
    String emptyNonce = "";
    walletClientGateway.createWalletUnitAttestation(session, emptyNonce, "wua")
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

  private String oidcLogin() {
    CookieFilter cookies = new CookieFilter();

    var redirectToKeycloak = given()
        .filter(cookies)
        .when().contentType(ContentType.JSON).body("bogusbody")
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
