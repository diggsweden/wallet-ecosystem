// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

public class DpopUtil {

  public static String createDpopProof(ECKey key, String htu, String htm) throws JOSEException {
    return createDpopProof(key, htu, htm, null);
  }

  public static String createDpopProof(ECKey key, String htu, String htm, String accessToken)
      throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("dpop+jwt"))
            .jwk(key.toPublicJWK())
            .build();
    JWTClaimsSet.Builder claimsBuilder =
        new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .issueTime(new Date())
            .claim("htu", htu)
            .claim("htm", htm);

    if (accessToken != null && !accessToken.isEmpty()) {
      try {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
        claimsBuilder.claim("ath", Base64URL.encode(hash).toString());
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
      }
    }

    SignedJWT signedJwt = new SignedJWT(header, claimsBuilder.build());
    signedJwt.sign(new ECDSASigner(key));
    return signedJwt.serialize();
  }
}
