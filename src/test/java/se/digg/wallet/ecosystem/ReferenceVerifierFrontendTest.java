// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@DisabledIfEnvironmentVariable(
    named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_USING_CUSTOM_HOSTS",
    matches = "true")
public class ReferenceVerifierFrontendTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://refimpl-verifier.wallet.local")
        .then()
        .assertThat().statusCode(200)
        .and().body("html.head.title", is("VerifierUi"));
  }
}
