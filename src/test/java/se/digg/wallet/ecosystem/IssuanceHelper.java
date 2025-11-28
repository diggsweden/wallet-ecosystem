// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class IssuanceHelper {

  private final KeycloakClient keycloak = new KeycloakClient();
  private final WalletProviderClient walletProvider = new WalletProviderClient();
  private final PidIssuerClient pidIssuer = new PidIssuerClient();

  public String issuePidCredential(ECKey bindingKey, ECKey encryptionKey) throws Exception {
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();
    String accessToken =
        keycloak.getDpopAccessToken(
            "pid-issuer-realm",
            userJwk,
            Map.of(
                "grant_type", "password",
                "client_id", "wallet-dev",
                "username", "tneal",
                "password", "password",
                "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                "role", "user"));

    String walletAttestation = walletProvider.getWalletUnitAttestation(bindingKey);
    String nonce = pidIssuer.getNonce(accessToken, userJwk);
    String proof = createProof(bindingKey, walletAttestation, nonce);
    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();
    Map<String, Object> payloadJson =
        pidIssuer
            .issueCredentials(
                accessToken, userJwk, encryptionKey, proof, pidIssuerCredentialRequestEncryptionKey)
            .toJSONObject();

    return extractSdJwtVc(payloadJson);
  }

  public String getIssuerChain() throws IOException {
    return new String(
        java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("config/issuer/issuer_chain.pem")));
  }

  private String createProof(ECKey jwk, String wua, String nonce) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .jwk(jwk.toPublicJWK())
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(jwk.toPublicJWK().toString())
            .audience(ServiceIdentifier.PID_ISSUER.toString())
            .issueTime(Date.from(Instant.now()))
            .claim("nonce", nonce)
            .claim("wua", wua)
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
