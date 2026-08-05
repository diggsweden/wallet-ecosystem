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
      throws JsonProcessingException, com.nimbusds.jose.JOSEException {
    String originalWua = walletProvider.getWalletUnitAttestation(bindingKey, nonce);
    try {
      java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
      try (java.io.InputStream is =
          new java.io.FileInputStream("config/certificates/wallet-provider/wallet_provider.p12")) {
        ks.load(is, "pass1234".toCharArray());
      }
      final java.security.PrivateKey privateKey =
          (java.security.PrivateKey) ks.getKey("wallet_provider", "pass1234".toCharArray());

      com.nimbusds.jwt.SignedJWT parsed = com.nimbusds.jwt.SignedJWT.parse(originalWua);
      final com.nimbusds.jose.JWSHeader header = parsed.getHeader();
      java.util.Map<String, Object> claimsMap =
          new java.util.HashMap<>(parsed.getJWTClaimsSet().getClaims());
      claimsMap.remove("eudi_wallet_info");
      claimsMap.remove("iss");

      com.nimbusds.jwt.JWTClaimsSet.Builder builder = new com.nimbusds.jwt.JWTClaimsSet.Builder();
      for (java.util.Map.Entry<String, Object> entry : claimsMap.entrySet()) {
        builder.claim(entry.getKey(), entry.getValue());
      }

      builder.claim("certification", "http://example.com/cert");
      java.util.Map<String, Object> statusObj =
          (java.util.Map<String, Object>) parsed.getJWTClaimsSet().getClaim("status");

      java.util.Map<String, Object> keyStorageStatusMap = new java.util.HashMap<>();
      if (statusObj != null) {
        java.util.Map<String, Object> newStatusObj = new java.util.HashMap<>(statusObj);
        java.util.Map<String, Object> sl =
            (java.util.Map<String, Object>) newStatusObj.get("status_list");
        if (sl != null) {
          java.util.Map<String, Object> newSl = new java.util.HashMap<>(sl);
          newSl.put("uri", "http://trust-source/signed/status-list.jwt");
          newStatusObj.put("status_list", newSl);
        }
        keyStorageStatusMap.put("status", newStatusObj);
      }
      keyStorageStatusMap.put("exp", (System.currentTimeMillis() / 1000) + (10 * 365 * 24 * 3600L));
      builder.claim("key_storage_status", keyStorageStatusMap);

      com.nimbusds.jwt.JWTClaimsSet newClaims = builder.build();
      com.nimbusds.jwt.SignedJWT newJwt = new com.nimbusds.jwt.SignedJWT(header, newClaims);

      com.nimbusds.jose.JWSSigner signer;
      if (privateKey instanceof java.security.interfaces.ECPrivateKey) {
        signer =
            new com.nimbusds.jose.crypto.ECDSASigner(
                (java.security.interfaces.ECPrivateKey) privateKey);
      } else {
        signer = new com.nimbusds.jose.crypto.RSASSASigner((java.security.PrivateKey) privateKey);
      }
      newJwt.sign(signer);
      return newJwt.serialize();
    } catch (Exception e) {
      throw new RuntimeException("Failed to rewrite WUA", e);
    }
  }
}
