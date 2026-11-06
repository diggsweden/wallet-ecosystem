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

  @Override
  public String createKeyAttestation(ECKey bindingKey, String nonce) {
    return attestationIssuer.getKeyAttestation(bindingKey, nonce);
  }
}
