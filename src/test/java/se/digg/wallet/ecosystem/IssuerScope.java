// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

/** The credential types the PID issuer can issue, with the scope and configuration id for each. */
public enum IssuerScope {
  PID("eu.europa.ec.eudi.pid_vc_sd_jwt", "eu.europa.ec.eudi.pid_vc_sd_jwt"),
  LEARNING_CREDENTIAL(
      "urn:eu.europa.ec.eudi:learning:credential:1:dc+sd-jwt",
      "urn:eu.europa.ec.eudi:learning:credential:1:dc+sd-jwt-compact");

  private final String scope;
  private final String credentialConfigurationId;

  IssuerScope(String scope, String credentialConfigurationId) {
    this.scope = scope;
    this.credentialConfigurationId = credentialConfigurationId;
  }

  public String scope() {
    return scope;
  }

  public String credentialConfigurationId() {
    return credentialConfigurationId;
  }
}
