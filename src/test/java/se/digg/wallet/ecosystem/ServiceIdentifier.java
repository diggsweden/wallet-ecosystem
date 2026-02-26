// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.net.URI;
import java.util.Optional;

public enum ServiceIdentifier {
  KEYCLOAK("https://localhost/idp",
      "DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI"),
  UNTRUSTED_KEYCLOAK("https://localhost/untrusted-idp",
      "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_KEYCLOAK_BASE_URI"),
  PID_ISSUER("https://localhost/pid-issuer",
      "DIGG_WALLET_ECOSYSTEM_PID_ISSUER_BASE_URI"),
  UNTRUSTED_PID_ISSUER("https://localhost/untrusted-pid-issuer",
      "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_PID_ISSUER_BASE_URI"),
  WALLET_PROVIDER("https://localhost/wallet-provider",
      "DIGG_WALLET_ECOSYSTEM_WALLET_PROVIDER_BASE_URI"),
  UNTRUSTED_WALLET_PROVIDER("https://localhost/untrusted-wallet-provider",
      "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_WALLET_PROVIDER_BASE_URI"),
  VERIFIER_BACKEND("https://localhost/refimpl-verifier-backend",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_BACKEND_BASE_URI"),
  VERIFIER_FRONTEND("https://localhost/demo-verifier",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_FRONTEND_BASE_URI"),
  WALLET_CLIENT_GATEWAY("https://localhost/wallet-client-gateway",
      "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_BASE_URI");

  private final String defaultUri;
  private final String environmentVariable;

  ServiceIdentifier(String defaultUri, String environmentVariable) {
    this.defaultUri = defaultUri;
    this.environmentVariable = environmentVariable;
  }

  private String getValue() {
    return Optional.ofNullable(System.getenv(environmentVariable))
        .orElse(defaultUri);
  }

  String getEnvironmentVariableName() {
    return environmentVariable;
  }

  @Override
  public String toString() {
    return getValue();
  }

  public URI toUri() {
    return URI.create(getValue());
  }

  public URI getResourceRoot() {
    return URI.create(getValue() + "/");
  }
}
