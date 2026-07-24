// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TrustSourceTest {

  private static final Pattern BASE_64 =
      Pattern.compile("(\\s*[a-zA-Z0-9/+]+)+={0,2}\\s*");

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

  @ParameterizedTest
  @ValueSource(strings = {
      "trusted-pid-issuers.xml",
      "trusted-pid-issuers-dss.xml"
  })
  void servesSignedListOfTrustedPidIssuers(String path) {
    trustSource.tryGet(path)
        .then()
        .assertThat().statusCode(200)
        .and().contentType("text/xml")
        .and().body(
            "TrustServiceStatusList.Signature.SignatureValue",
            matchesPattern(BASE_64))
        .and().body(
            "TrustServiceStatusList.Signature.KeyInfo.X509Data.X509Certificate",
            matchesPattern(BASE_64));
  }
}
