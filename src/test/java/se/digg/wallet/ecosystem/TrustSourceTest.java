// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

class TrustSourceTest {

  private final TrustSourceClient trustSource = new TrustSourceClient();

  @Test
  void isHealthy() {
    trustSource.tryGet("dummy.xml")
        .then()
        .assertThat().statusCode(200)
        .and().contentType("text/xml")
        .and().body("xml", is("Hello world!"));
  }

  @Test
  void servesListOfTrustedPidIssuers() {
    trustSource.tryGet("trusted-pid-issuers.xml")
        .then()
        .assertThat().statusCode(200)
        .and().contentType("text/xml")
        .and().body("TrustServiceStatusList.@Id", is("trusted-pid-issuers"));
  }
}
