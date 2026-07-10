// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.util.Map;
import java.util.Optional;

public enum Property {
  KEYCLOAK_BASE_URI("https://localhost/idp"),
  KEYCLOAK_INTERNAL_BASE_URI("https://localhost/idp-internal"),
  UNTRUSTED_KEYCLOAK_BASE_URI("https://localhost/untrusted-idp"),
  PID_ISSUER_BASE_URI("https://localhost/pid-issuer"),
  UNTRUSTED_PID_ISSUER_BASE_URI("https://localhost/untrusted-pid-issuer"),
  WALLET_PROVIDER_BASE_URI("https://localhost/wallet-provider"),
  UNTRUSTED_WALLET_PROVIDER_BASE_URI("https://localhost/untrusted-wallet-provider"),
  VERIFIER_BACKEND_BASE_URI("https://localhost/refimpl-verifier-backend"),
  VERIFIER_FRONTEND_BASE_URI("https://localhost/demo-verifier"),
  TRUST_VALIDATOR_BASE_URI("https://localhost/trust-validator"),
  TRUST_SOURCE_BASE_URI("https://localhost/trust-source"),
  WALLET_CLIENT_GATEWAY_BASE_URI("https://localhost/wallet-client-gateway"),
  WALLET_CLIENT_GATEWAY_API_KEY("apikey"),
  VERIFIER_AUDIENCE("x509_san_dns:localhost");

  private final String defaultValue;

  Property(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getValue() {
    return getValue(System.getenv());
  }

  String getValue(Map<String, String> environment) {
    return Optional.ofNullable(environment.get(getEnvironmentVariableName())).orElse(defaultValue);
  }

  String getEnvironmentVariableName() {
    return String.format("DIGG_WALLET_ECOSYSTEM_%s", name());
  }
}
