// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.SSLConfig.sslConfig;

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
import io.restassured.filter.session.SessionFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
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
  private static String session = "";

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
  void createSessionTest() throws Exception {
    var ecKey = generateKey();

    var accountId = createAccount(ecKey);
    System.out.println("Expected an account " + accountId);
    // var nonce = initChallenge(accountId);
    // var signedJwt = createSignedJwt(ecKey, nonce, accountId);
    // var sessionHeader = respondToChallenge(signedJwt);

    session = "sessionHeader";
  }



  @Test
  @Order(99)
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

  private String createAccount(ECKey ecKey) {
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
    var sessionId = springSession.header("session");
    System.out.println("Session " + springSession);
    System.out.print("redirect URL " + redirectUrl); //need https ?
    redirectUrl =  redirectUrl.replace("http:", "https:");

    var keyCloakSession =  new SessionFilter();
  
    var myProviderResponse =  given()
      .filter(keyCloakSession)
      .filter(cookies)
      .redirects().follow(false)
      .get(redirectUrl)
      .then()
      .extract()
      .response();

    System.out.println("Status on myprovider" + myProviderResponse.getStatusCode());  

    var redirectUrltoKeyCloak = myProviderResponse.getHeader("Location");
    var sessionId2 = myProviderResponse.header("session");
    System.out.println("Session " + springSession.toString());
    System.out.print("redirectUrltoKeyCloak " + redirectUrltoKeyCloak  +  "  session2 "+  sessionId2);
     redirectUrltoKeyCloak =  redirectUrltoKeyCloak.replace("http:", "https:");
     var keycloakLoginPage = given()
                .filter(cookies)
                 .filter(keyCloakSession)
                .redirects().follow(true)
                .get(redirectUrltoKeyCloak);
     String loginAction = keycloakLoginPage.htmlPath().getString("**.find { it.@id=='kc-form-login' }.@action");
        System.out.println("Keycloak login action: " + loginAction);

     var loginResponse = given()
                .filter(cookies)
                .filter(keyCloakSession)
                .redirects().follow(false)
                .formParam("username", "test1")
                .formParam("password", "test1")
                .post(loginAction);

     String backToApp = loginResponse.getHeader("Location");
      System.out.println("Redirect after login: " + backToApp);
    var applicationResponse = given()
          .filter(cookies)
          .filter(keyCloakSession)
          .redirects().follow(false)
          .get(backToApp);
    System.out.println("Result: " + applicationResponse.statusCode());
    System.out.println("Final session:  " + applicationResponse.getSessionId());
     System.out.println("Final header:  " + applicationResponse.headers().toString());
    System.out.println("We have some session ID: " + keyCloakSession.getSessionId());
    return keyCloakSession.getSessionId();

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

    // Compute the EC signature
    signedJwt.sign(signer);

    // Serialize the JWS to compact form
    return signedJwt.serialize();
  }

   private final String APP_BASE = "https://localhost/wallet-client-gateway";
    private final String KEYCLOAK_BASE = "http://localhost:8180";
    private final String REALM = "myrealm";
    private final String CLIENT_ID = "myclient";
    
    @Test
    void testOidcLoginFlow() {

        CookieFilter cookies = new CookieFilter();

        //
        // 1️⃣ Call your app (this endpoint triggers Spring Security redirect to OIDC)
        //
        var createAccount = given()
                .filter(cookies)
                .redirects().follow(false)
                .post(APP_BASE + "/oidc/createAccount");

        String authRedirect = createAccount.getHeader("Location").replace("http", "https");
        System.out.println("Redirected to: " + authRedirect);

        // assertThat(authRedirect, containsString("/realms/" + REALM + "/protocol/openid-connect/auth"));
        var springRoutePage = given()
                .filter(cookies)
                .redirects().follow(false)
                .get(authRedirect)
                ;
        String keycloakRedirect = springRoutePage.getHeader("Location");
        System.out.println("Redirected to: " + keycloakRedirect);
        //
        // 2️⃣ Follow redirect to Keycloak login page (GET)
        //
        var keycloakLoginPage = given()
                .filter(cookies)
                .redirects().follow(true)
                .get(keycloakRedirect);

        // Extract login form action URL (Keycloak changes this per login session)
        String loginAction = keycloakLoginPage.htmlPath().getString("**.find { it.@id=='kc-form-login' }.@action");
        System.out.println("Keycloak login action: " + loginAction);

        //
        // 3️⃣ POST username/password to Keycloak login form
        //
        var loginResponse = given()
                .filter(cookies)
                .redirects().follow(false)
                .formParam("username", "testuser")
                .formParam("password", "testpassword")
                .post(loginAction);

        String backToApp = loginResponse.getHeader("Location");
        // assertThat(backToApp, containsString(APP_BASE));

        System.out.println("Redirect after login: " + backToApp);

        //
        // 4️⃣ Follow redirect back to your Spring Boot app (authorization_code callback)
        //
        var callbackResponse = given()
                .filter(cookies)
                .redirects().follow(true)
                .get(backToApp);

        //
        // 5️⃣ Spring Boot now automatically exchanges the authorization code with Keycloak.
        //     If successful, it issues a SESSION cookie.
        //

        String sessionId = "";
        System.out.println("SESSION cookie = " + sessionId);

        // assertThat("Spring session must exist after successful OIDC login", sessionId != null);

        //
        // 6️⃣ Optional: Access a secured endpoint with the session cookie
        //
        // var secured = RestAssured.given()
        //         .filter(cookies)
        //         .get(APP_BASE + "/secured/profile");

        // secured.then().statusCode(200);
        // System.out.println("Secured content: " + secured.getBody().asString());
    }

}
