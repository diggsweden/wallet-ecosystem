// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
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
import java.util.Date;
import java.util.UUID;

public class DpopUtil {

  public static String createDpopProof(ECKey key, String htu, String htm) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("dpop+jwt"))
            .jwk(key.toPublicJWK())
            .build();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .issueTime(new Date())
            .claim("htu", htu)
            .claim("htm", htm)
            .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(new ECDSASigner(key));
    return signedJwt.serialize();
  }
}
