// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Optional;

public class PublicWalletClient implements WalletClient {

  private static final String API_KEY = Optional.ofNullable(System.getenv(
      "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_API_KEY")).orElse("apikey");

  private final WalletClientGatewayClient gateway = new WalletClientGatewayClient();

  public String createWalletUnitAttestation(ECKey bindingKey, String nonce) throws JOSEException {
    var accountId = gateway.createAccount(
        "{\"deviceKey\": %s}".formatted(bindingKey.toPublicJWK().toJSONString()));
    var sessionNonce = gateway.initChallenge(accountId, bindingKey.getKeyID());
    var signedJwt = createSignedJwt(bindingKey, sessionNonce);
    var session = gateway.respondToChallenge(signedJwt);
    return gateway.tryCreateWalletUnitAttestation(session, nonce).then()
        .assertThat().statusCode(201).extract()
        .body().jsonPath().getString("jwt");
  }

  private static String createSignedJwt(ECKey ecJwk, String nonce)
      throws JOSEException {
    var claims = new JWTClaimsSet.Builder()
        .claim("nonce", nonce)
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).build();
    var signedJwt = new SignedJWT(header, claims);

    signedJwt.sign(new ECDSASigner(ecJwk));

    return signedJwt.serialize();
  }
}
