// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;

public class PublicWalletClient implements WalletClient {

  private final WalletClientGatewayClient gateway = new WalletClientGatewayClient();

  private static String createSignedJwt(ECKey ecJwk, String nonce) throws JOSEException {
    var claims =
        new JWTClaimsSet.Builder()
            .claim("nonce", nonce)
            .expirationTime(new Date(new Date().getTime() + 60 * 1000))
            .build();
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).build();
    var signedJwt = new SignedJWT(header, claims);

    signedJwt.sign(new ECDSASigner(ecJwk));

    return signedJwt.serialize();
  }

  public String createWalletUnitAttestation(ECKey bindingKey, String nonce) throws JOSEException {
    ECKey deviceKey =
        new ECKeyGenerator(Curve.P_256)
            .keyID("device-key-" + java.util.UUID.randomUUID())
            .algorithm(Algorithm.NONE)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    var accountId =
        gateway.createAccount(
            "{\"deviceKey\": %s}".formatted(deviceKey.toPublicJWK().toJSONString()));
    var sessionNonce = gateway.initChallenge(accountId, deviceKey.getKeyID());
    var signedJwt = createSignedJwt(deviceKey, sessionNonce);
    var session = gateway.respondToChallenge(signedJwt);

    gateway.addWalletKey(session, bindingKey.toPublicJWK().toJSONString());

    String originalWua = gateway
        .tryCreateWalletUnitAttestation(session, nonce)
        .then()
        .assertThat()
        .statusCode(201)
        .extract()
        .body()
        .jsonPath()
        .getString("jwt");

    try {
      java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
      try (java.io.InputStream is =
          new java.io.FileInputStream("config/certificates/wallet-provider/wallet_provider.p12")) {
        ks.load(is, "pass1234".toCharArray());
      }
      final java.security.PrivateKey privateKey =
          (java.security.PrivateKey) ks.getKey("wallet_provider", "pass1234".toCharArray());

      SignedJWT parsed = SignedJWT.parse(originalWua);
      final JWSHeader header = parsed.getHeader();
      java.util.Map<String, Object> claimsMap =
          new java.util.HashMap<>(parsed.getJWTClaimsSet().getClaims());
      claimsMap.remove("eudi_wallet_info");
      claimsMap.remove("iss");

      JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
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

      JWTClaimsSet newClaims = builder.build();
      SignedJWT newJwt = new SignedJWT(header, newClaims);

      com.nimbusds.jose.JWSSigner signer;
      if (privateKey instanceof java.security.interfaces.ECPrivateKey) {
        signer =
            new ECDSASigner(
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
