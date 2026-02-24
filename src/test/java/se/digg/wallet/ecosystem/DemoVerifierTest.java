// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.containsString;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import org.junit.jupiter.api.Test;

public class DemoVerifierTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://localhost/demo-verifier")
        .then()
        .assertThat().statusCode(200)
        .and().body(containsString("demoapplikationer"));
  }
}
