// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class DpopUtilTest {

  @Test
  void createDpopProof_withAccessToken_includesCorrectAthClaim() throws Exception {
    ECKey key = new ECKeyGenerator(Curve.P_256).generate();
    String accessToken = "vF9dft4qmTc2Nvb3RlckRhdGE";

    // Expected hash of "vF9dft4qmTc2Nvb3RlckRhdGE" as base64url
    // using RFC 9449 Appendix C example
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    byte[] hash = md.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
    String expectedAth = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

    String proof =
        DpopUtil.createDpopProof(key, "https://server.example.com/token", "POST", accessToken);
    SignedJWT signedJwt = SignedJWT.parse(proof);

    // Verify signature
    ECDSAVerifier verifier = new ECDSAVerifier(key.toECPublicKey());
    org.junit.jupiter.api.Assertions.assertTrue(signedJwt.verify(verifier));

    // Verify ath claim
    String actualAth = signedJwt.getJWTClaimsSet().getStringClaim("ath");
    assertNotNull(actualAth);
    assertEquals(expectedAth, actualAth);
  }

  @Test
  void createDpopProof_withoutAccessToken_doesNotIncludeAthClaim() throws Exception {
    ECKey key = new ECKeyGenerator(Curve.P_256).generate();

    String proof = DpopUtil.createDpopProof(key, "https://server.example.com/token", "POST");
    SignedJWT signedJwt = SignedJWT.parse(proof);

    // Verify ath claim is missing
    String actualAth = signedJwt.getJWTClaimsSet().getStringClaim("ath");
    assertNull(actualAth);
  }
}
