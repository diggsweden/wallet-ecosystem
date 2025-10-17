// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;

import org.junit.jupiter.api.Test;

public class WalletProviderTest {

  @Test
  void isHealthy() {
    given()
        .relaxedHTTPSValidation()
        .when()
        .get("https://wallet-provider.wallet.local/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }
}
