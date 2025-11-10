// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class KeycloakTest {

  public static final String BASE_URL = "https://localhost/idp";
  public static final String PID_ISSUER_REALM = BASE_URL + "/realms/pid-issuer-realm";
  public static final String TOKEN_ENDPOINT = PID_ISSUER_REALM + "/protocol/openid-connect/token";

  @ParameterizedTest
  @ValueSource(strings = {
      "/health/live",
      "/health/ready",
      "/health/started",
      "/health"
  })
  void isHealthy(String path) {
    given()
        .when()
        .get(BASE_URL + path)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", equalTo("UP"));
  }

  @Test
  void servesPidIssuerRealm() {
    given()
        .when()
        .get(PID_ISSUER_REALM)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("realm", equalTo("pid-issuer-realm"));
  }

  @Test
  void canGetDpopAccessTokenForClientCredentials() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();
    String dpopProof = DpopUtil.createDpopProof(jwk, TOKEN_ENDPOINT, "POST");

    given()
        .when()
        .contentType(ContentType.URLENC)
        .header("DPoP", dpopProof)
        .formParam("grant_type", "client_credentials")
        .formParam("client_id", "pid-issuer-srv")
        .formParam("client_secret", "zIKAV9DIIIaJCzHCVBPlySgU8KgY68U2")
        .post(TOKEN_ENDPOINT)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue())
        .body("token_type", equalTo("DPoP"));
  }

  @Test
  void canGetDpopAccessTokenForUser() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();
    String dpopProof = DpopUtil.createDpopProof(jwk, TOKEN_ENDPOINT, "POST");

    given()
        .when()
        .contentType(ContentType.URLENC)
        .header("DPoP", dpopProof)
        .formParam("grant_type", "password")
        .formParam("client_id", "wallet-dev")
        .formParam("username", "tneal")
        .formParam("password", "password")
        .post(TOKEN_ENDPOINT)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue())
        .body("token_type", equalTo("DPoP"));
  }

  @Test
  void isAccessibleOnAlternateUrl() {
    given()
        .when()
        .get("https://localhost/.well-known/oauth-authorization-server/idp/realms/pid-issuer-realm")
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", equalTo("https://localhost/idp/realms/pid-issuer-realm"));
  }
}
