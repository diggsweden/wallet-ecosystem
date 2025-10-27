// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.hasItem;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

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
}
