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
            .keyID("device-key-123")
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

    return gateway
        .tryCreateWalletUnitAttestation(session, nonce)
        .then()
        .assertThat()
        .statusCode(201)
        .extract()
        .body()
        .jsonPath()
        .getString("jwt");
  }
}
