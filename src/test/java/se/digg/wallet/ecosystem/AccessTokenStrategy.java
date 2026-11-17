// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import java.util.HashMap;
import java.util.Map;

public enum AccessTokenStrategy {
  DPOP {
    @Override
    public String applyTo(
        WalletAttestationIssuerClient attestationIssuer, KeycloakClient keycloak,
        String clientId, ECKey bindingKey, Map<String, String> parameters) throws JOSEException {

      HashMap<String, String> extended = new HashMap<>(parameters);
      extended.put("client_id", clientId);
      return keycloak.getDpopAccessToken("pid-issuer-realm", bindingKey, extended);
    }
  },
  DPOP_WITH_CLIENT_ATTESTATION {
    @Override
    public String applyTo(
        WalletAttestationIssuerClient attestationIssuer, KeycloakClient keycloak,
        String clientId, ECKey bindingKey, Map<String, String> parameters) throws JOSEException {

      String wia = attestationIssuer.getWalletInstanceAttestation(bindingKey);
      return keycloak.getDpopAccessTokenWithClientAttestation(
          "pid-issuer-realm", clientId, bindingKey, wia, parameters);
    }
  };

  public abstract String applyTo(
      WalletAttestationIssuerClient attestationIssuer,
      KeycloakClient keycloak,
      String clientId,
      ECKey bindingKey,
      Map<String, String> parameters) throws JOSEException;
}
