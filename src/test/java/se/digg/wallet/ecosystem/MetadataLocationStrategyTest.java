// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static se.digg.wallet.ecosystem.MetadataLocationStrategy.BASIC;
import static se.digg.wallet.ecosystem.MetadataLocationStrategy.OID4VCI_COMPLIANT;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MetadataLocationStrategyTest {

  public static Stream<Arguments> uriExamples() {
    return Stream.of(
        Arguments.of(BASIC, "https://issuer.example.com/tenant",
            "https://issuer.example.com/tenant/.well-known/openid-credential-issuer"),
        Arguments.of(BASIC, "https://issuer.example.com",
            "https://issuer.example.com/.well-known/openid-credential-issuer"),
        Arguments.of(OID4VCI_COMPLIANT, "https://issuer.example.com/tenant",
            "https://issuer.example.com/.well-known/openid-credential-issuer/tenant"),
        Arguments.of(OID4VCI_COMPLIANT, "https://issuer.example.com",
            "https://issuer.example.com/.well-known/openid-credential-issuer"));
  }

  @ParameterizedTest
  @MethodSource("uriExamples")
  void producesExpectedUris(MetadataLocationStrategy strategy, URI identifier, URI expected) {
    assertThat(strategy.applyTo(identifier, "/.well-known/openid-credential-issuer"), is(expected));
  }
}
