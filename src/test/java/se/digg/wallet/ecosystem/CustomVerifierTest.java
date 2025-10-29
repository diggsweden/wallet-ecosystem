// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.containsString;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import org.junit.jupiter.api.Test;

public class CustomVerifierTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://custom-verifier.wallet.local")
        .then()
        .assertThat().statusCode(200)
        .and().body(containsString("Strumpsorteringscentralen"));
  }
}
