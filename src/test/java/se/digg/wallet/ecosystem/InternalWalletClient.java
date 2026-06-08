// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2
package se.digg.wallet.ecosystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.jwk.ECKey;

public class InternalWalletClient implements WalletClient {

  private final WalletProviderClient walletProvider;

  public InternalWalletClient() {
    this(new WalletProviderClient());
  }

  public InternalWalletClient(WalletProviderClient walletProvider) {
    this.walletProvider = walletProvider;
  }

  public String createWalletUnitAttestation(ECKey bindingKey, String nonce)
      throws JsonProcessingException {
    return walletProvider.getWalletUnitAttestation(bindingKey, nonce);
  }
}
