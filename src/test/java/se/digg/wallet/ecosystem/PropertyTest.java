// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class PropertyTest {

  private static final String[] EXPECTED_NAMES = new String[] {
      "DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_KEYCLOAK_INTERNAL_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_KEYCLOAK_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_PID_ISSUER_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_PID_ISSUER_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_WALLET_PROVIDER_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_WALLET_PROVIDER_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_BACKEND_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_FRONTEND_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_TRUST_SOURCE_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_BASE_URI",
      "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_API_KEY",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_AUDIENCE"
  };

  @ParameterizedTest
  @EnumSource(Property.class)
  void hasExpectedName(Property p) {
    assertThat(p.getEnvironmentVariableName(), is(oneOf(EXPECTED_NAMES)));
  }

  @ParameterizedTest
  @CsvSource({
      "VERIFIER_FRONTEND_BASE_URI,https://example.com/verifier-frontend",
      "VERIFIER_BACKEND_BASE_URI,https://example.com/verifier-backend",
  })
  void prefersValueFromEnvironment(String propertyName, String expectedValue) {
    // Given
    Property property = Enum.valueOf(Property.class, propertyName);
    Map<String, String> environment = Map.of(
        "DIGG_WALLET_ECOSYSTEM_VERIFIER_FRONTEND_BASE_URI", "https://example.com/verifier-frontend",
        "DIGG_WALLET_ECOSYSTEM_VERIFIER_BACKEND_BASE_URI", "https://example.com/verifier-backend");

    // Then
    assertThat(property.getValue(environment), is(expectedValue));
  }

  @ParameterizedTest
  @EnumSource(Property.class)
  void hasDefaultValue(Property p) {
    assertThat(p.getValue(Collections.emptyMap()), not(is(blankOrNullString())));
  }
}
