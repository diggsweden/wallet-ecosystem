// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.jwk.ECKey;

public class InternalWalletClient implements WalletClient {

  private final WalletAttestationIssuerClient attestationIssuer;

  public InternalWalletClient() {
    this(new WalletAttestationIssuerClient());
  }

  public InternalWalletClient(WalletAttestationIssuerClient attestationIssuer) {
    this.attestationIssuer = attestationIssuer;
  }

  @Deprecated
  public static WalletClient deprecatedWua() {
    return deprecatedWua(new WalletAttestationIssuerClient());
  }

  @Deprecated
  public static WalletClient deprecatedWua(WalletAttestationIssuerClient attestationIssuer) {
    return (bindingKey, nonce) -> attestationIssuer.getWalletUnitAttestation(bindingKey, nonce);
  }

  @Override
  public String createKeyAttestation(ECKey bindingKey, String nonce) {
    return attestationIssuer.getKeyAttestation(bindingKey, nonce);
  }
}
