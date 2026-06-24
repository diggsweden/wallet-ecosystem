// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.net.URI;

public enum ServiceIdentifier {
  KEYCLOAK,
  KEYCLOAK_INTERNAL,
  UNTRUSTED_KEYCLOAK,
  PID_ISSUER,
  UNTRUSTED_PID_ISSUER,
  WALLET_PROVIDER,
  UNTRUSTED_WALLET_PROVIDER,
  VERIFIER_BACKEND,
  VERIFIER_FRONTEND,
  WALLET_CLIENT_GATEWAY;

  private String getValue() {
    return Enum.valueOf(Property.class, name() + "_BASE_URI").getValue();
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
