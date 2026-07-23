// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;

public interface WalletClient {
  String createWalletUnitAttestation(ECKey bindingKey, String nonce)
      throws JsonProcessingException, JOSEException;
}
