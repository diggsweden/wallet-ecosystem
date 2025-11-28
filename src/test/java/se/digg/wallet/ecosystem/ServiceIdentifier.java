// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.net.URI;
import java.util.Optional;

public enum ServiceIdentifier {
  KEYCLOAK("https://localhost/idp",
      "DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI"),
  PID_ISSUER("https://localhost/pid-issuer",
      "DIGG_WALLET_ECOSYSTEM_PID_ISSUER_BASE_URI"),
  WALLET_PROVIDER("https://localhost/wallet-provider",
      "DIGG_WALLET_ECOSYSTEM_WALLET_PROVIDER_BASE_URI"),
  VERIFIER_BACKEND("https://localhost/refimpl-verifier-backend",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_BACKEND_BASE_URI"),
  VERIFIER_FRONTEND("https://localhost/custom-verifier",
      "DIGG_WALLET_ECOSYSTEM_VERIFIER_FRONTEND_BASE_URI");

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
