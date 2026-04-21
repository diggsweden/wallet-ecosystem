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
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public final class VerifiablePresentationToken {
  private VerifiablePresentationToken() {}

  public static String asString(String sdJwtVc, ECKey bindingKey, String nonce)
      throws NoSuchAlgorithmException, JOSEException {

    SignedJWT kbJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .jwk(bindingKey.toPublicJWK())
                .type(new JOSEObjectType("kb+jwt"))
                .build(),
            new JWTClaimsSet.Builder()
                .audience(VerifierBackendClient.VERIFIER_AUDIENCE)
                .issueTime(Date.from(Instant.now()))
                .claim("nonce", nonce)
                .claim("sd_hash", Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                        .digest(sdJwtVc.getBytes(StandardCharsets.UTF_8))))
                .build());
    kbJwt.sign(new ECDSASigner(bindingKey));
    return sdJwtVc + kbJwt.serialize();
  }
}
