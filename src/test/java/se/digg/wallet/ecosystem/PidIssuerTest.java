// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.SSLConfig.sslConfig;
import static org.hamcrest.Matchers.hasItem;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

public class PidIssuerTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://pid-issuer.wallet.local/.well-known/openid-credential-issuer")
        .then()
        .assertThat().statusCode(200)
        .and().body(
            "authorization_servers",
            hasItem("https://keycloak.wallet.local/idp/realms/pid-issuer-realm"));
  }

  private static RequestSpecification given() {
    return RestAssured.given().config(RestAssured.config()
        .sslConfig(sslConfig().relaxedHTTPSValidation())
        .logConfig(logConfig().enableLoggingOfRequestAndResponseIfValidationFails()));
  }
}
