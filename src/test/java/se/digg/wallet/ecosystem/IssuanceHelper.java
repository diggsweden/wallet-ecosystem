// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
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

public class IssuanceHelper {
  private final KeycloakClient keycloak;
  private final WalletProviderClient walletProvider;
  private final PidIssuerClient pidIssuer;
  private final String audience;

  public IssuanceHelper() {
    this(new WalletProviderClient());
  }

  public IssuanceHelper(WalletProviderClient walletProvider) {
    this(new KeycloakClient(), walletProvider, new PidIssuerClient(),
        ServiceIdentifier.PID_ISSUER.toString());
  }

  public IssuanceHelper(
      KeycloakClient keycloak,
      WalletProviderClient walletProvider,
      PidIssuerClient pidIssuer,
      String audience) {

    this.keycloak = keycloak;
    this.walletProvider = walletProvider;
    this.pidIssuer = pidIssuer;
    this.audience = audience;
  }

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
    String walletAttestation = walletProvider.getWalletUnitAttestation(bindingKey, nonce);
    String proof = createProof(bindingKey, walletAttestation, nonce);
    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();
    Map<String, Object> payloadJson =
        pidIssuer
            .issueCredentials(
                accessToken, userJwk, encryptionKey, proof, pidIssuerCredentialRequestEncryptionKey)
            .toJSONObject();

    return extractSdJwtVc(payloadJson);
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
