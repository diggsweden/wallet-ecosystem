// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.List;
import java.util.Map;

public final class DirectPostJwtResponse {

  private DirectPostJwtResponse() {}

  public static String create(
      SignedJWT signedAuthRequest, String state, String dcqlId, String vpToken, String nonce)
      throws ParseException, JOSEException {
    return create(signedAuthRequest, state, Map.of(dcqlId, List.of(vpToken)), nonce);
  }

  public static String create(
      SignedJWT signedAuthRequest, String state, Map<String, List<String>> vpTokens, String nonce)
      throws ParseException, JOSEException {

    JWTClaimsSet authClaims = signedAuthRequest.getJWTClaimsSet();
    Map<String, Object> clientMetadata = authClaims.getJSONObjectClaim("client_metadata");
    if (clientMetadata == null) {
      throw new IllegalArgumentException("client_metadata is missing in authorization request");
    }

    Object jwksObj = clientMetadata.get("jwks");
    if (jwksObj == null) {
      throw new IllegalArgumentException("jwks is missing in client_metadata");
    }

    JWKSet jwkSet;
    if (jwksObj instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> jwksMap = (Map<String, Object>) jwksObj;
      jwkSet = JWKSet.parse(jwksMap);
    } else {
      jwkSet = JWKSet.parse(jwksObj.toString());
    }

    ECKey verifierKey =
        jwkSet.getKeys().stream()
            .filter(ECKey.class::isInstance)
            .map(ECKey.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No EC key found in verifier jwks"));

    JWEAlgorithm alg =
        verifierKey.getAlgorithm() != null
            ? JWEAlgorithm.parse(verifierKey.getAlgorithm().getName())
            : JWEAlgorithm.ECDH_ES;

    @SuppressWarnings("unchecked")
    List<String> encValues =
        (List<String>) clientMetadata.get("encrypted_response_enc_values_supported");
    EncryptionMethod enc =
        (encValues != null && !encValues.isEmpty())
            ? EncryptionMethod.parse(encValues.getFirst())
            : EncryptionMethod.A128GCM;

    JWEHeader jweHeader =
        new JWEHeader.Builder(alg, enc)
            .agreementPartyVInfo(Base64URL.encode(nonce))
            .build();

    JWTClaimsSet responseClaims =
        new JWTClaimsSet.Builder()
            .claim("state", state)
            .claim("vp_token", vpTokens)
            .build();

    EncryptedJWT encryptedJwt = new EncryptedJWT(jweHeader, responseClaims);
    encryptedJwt.encrypt(new ECDHEncrypter(verifierKey));
    return encryptedJwt.serialize();
  }
}
