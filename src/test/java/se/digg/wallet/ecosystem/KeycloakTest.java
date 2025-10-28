// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.notNullValue;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

public class KeycloakTest {

  public static final String PID_ISSUER_REALM =
      "https://keycloak.wallet.local/idp/realms/pid-issuer-realm";

  @Test
  void isHealthy() {
    given().when().get(PID_ISSUER_REALM).then().assertThat().statusCode(200);
  }

  @Test
  void hasOpenIdConfiguration() {
    given()
        .when()
        .get(PID_ISSUER_REALM + "/.well-known/openid-configuration")
        .then()
        .assertThat()
        .statusCode(200);
  }

  @Test
  void canGetAccessToken() {
    given()
        .when()
        .contentType(ContentType.URLENC)
        .formParam("grant_type", "client_credentials")
        .formParam("client_id", "pid-issuer-srv")
        .formParam("client_secret", "zIKAV9DIIIaJCzHCVBPlySgU8KgY68U2")
        .post(PID_ISSUER_REALM + "/protocol/openid-connect/token")
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue());
  }

  @Test
  void canGetAccessTokenForUser() {
    given()
        .when()
        .contentType(ContentType.URLENC)
        .formParam("grant_type", "password")
        .formParam("client_id", "wallet-dev")
        .formParam("username", "tneal")
        .formParam("password", "password")
        .post(PID_ISSUER_REALM + "/protocol/openid-connect/token")
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue());
  }
}
