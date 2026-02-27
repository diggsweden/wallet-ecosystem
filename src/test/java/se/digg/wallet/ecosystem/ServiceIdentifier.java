// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.net.URI;
import java.util.Optional;

public enum ServiceIdentifier {
  KEYCLOAK("https://localhost/idp"),
  UNTRUSTED_KEYCLOAK("https://localhost/untrusted-idp"),
  PID_ISSUER("https://localhost/pid-issuer"),
  UNTRUSTED_PID_ISSUER("https://localhost/untrusted-pid-issuer"),
  WALLET_PROVIDER("https://localhost/wallet-provider"),
  UNTRUSTED_WALLET_PROVIDER("https://localhost/untrusted-wallet-provider"),
  VERIFIER_BACKEND("https://localhost/refimpl-verifier-backend"),
  VERIFIER_FRONTEND("https://localhost/demo-verifier"),
  WALLET_CLIENT_GATEWAY("https://localhost/wallet-client-gateway");

  private final String defaultUri;

  ServiceIdentifier(String defaultUri) {
    this.defaultUri = defaultUri;
  }

  private String getValue() {
    return Optional.ofNullable(System.getenv(getEnvironmentVariableName()))
        .orElse(defaultUri);
  }

  String getEnvironmentVariableName() {
    return String.format("DIGG_WALLET_ECOSYSTEM_%s_BASE_URI", name());
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
