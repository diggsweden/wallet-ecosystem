// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class TrustValidatorTest {

  private final TrustValidatorClient trustValidator = new TrustValidatorClient();

  @Test
  void isHealthy() {
    trustValidator.tryGetHealth()
        .then().assertThat().statusCode(is(200))
        .and().body("status", is("UP"));
  }
}
