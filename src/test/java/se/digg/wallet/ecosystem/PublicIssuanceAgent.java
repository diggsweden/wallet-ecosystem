// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PublicIssuanceAgent implements IssuanceAgent {

  private static final String API_KEY = Optional.ofNullable(System.getenv(
      "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_API_KEY")).orElse("apikey");

  private final KeycloakClient keycloak = new KeycloakClient();
  private final WalletClientGatewayClient gateway = new WalletClientGatewayClient();
  private final PidIssuerClient pidIssuer = new PidIssuerClient();
  private final String audience = ServiceIdentifier.PID_ISSUER.toString();

  @Override
  public String issuePidCredential(ECKey bindingKey, String username, String password)
      throws Exception {

    ECKey encryptionKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();

    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();
    String accessToken =
        keycloak.getDpopAccessToken(
            "pid-issuer-realm",
            userJwk,
            Map.of(
                "grant_type", "password",
                "client_id", "wallet-dev",
                "username", username,
                "password", password,
                "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                "role", "user"));

    String nonce = pidIssuer.getNonce(accessToken, userJwk);

    var accountId = gateway.createAccount(
        "{\"deviceKey\": %s}".formatted(bindingKey.toPublicJWK().toJSONString()));
    var sessionNonce = gateway.initChallenge(accountId, bindingKey.getKeyID());
    var signedJwt = createSignedJwt(bindingKey, sessionNonce);
    var session = gateway.respondToChallenge(signedJwt);
    String walletAttestation = gateway.tryCreateWalletUnitAttestation(session, nonce).then()
        .assertThat().statusCode(201).extract()
        .body().jsonPath().getString("jwt");
    String proof = createProof(bindingKey, walletAttestation, nonce);
    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();
    Map<String, Object> payloadJson =
        pidIssuer
            .issueCredentials(
                accessToken, userJwk, encryptionKey, proof, pidIssuerCredentialRequestEncryptionKey)
            .toJSONObject();

    return extractSdJwtVc(payloadJson);
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

  private String createProof(ECKey jwk, String wua, String nonce) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .customParam("key_attestation", wua)
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(jwk.toPublicJWK().toString())
            .audience(audience)
            .issueTime(Date.from(Instant.now()))
            .claim("nonce", nonce)
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(jwk));
    return jwt.serialize();
  }

  private String extractSdJwtVc(Map<String, Object> payloadJson) {
    var credentials = (List<?>) payloadJson.get("credentials");
    Object firstCredential = credentials.getFirst();
    Map<String, Object> credentialMap = (Map<String, Object>) firstCredential;
    return (String) credentialMap.get("credential");
  }
}
