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

public class IssuanceAgent {

  public static final String DEFAULT_CLIENT_ID = "eudiw";
  public static final String LEGACY_CLIENT_ID = "wallet-dev";

  private static final String DEFAULT_KA_KEY_ID = "0";

  public static Builder builder() {
    return new Builder();
  }

  static IssuanceAgent untrusted() {
    var untrustedAttestationIssuer =
        new WalletAttestationIssuerClient(
            ServiceIdentifier.UNTRUSTED_WALLET_ATTESTATION_ISSUER.getResourceRoot());
    return builder()
        .wallet(new InternalWalletClient(untrustedAttestationIssuer))
        .keycloak(new KeycloakClient(ServiceIdentifier.UNTRUSTED_KEYCLOAK.getResourceRoot()))
        .pidIssuer(new PidIssuerClient(ServiceIdentifier.UNTRUSTED_PID_ISSUER.getResourceRoot()))
        .attestationIssuer(untrustedAttestationIssuer)
        .audience(ServiceIdentifier.UNTRUSTED_PID_ISSUER.toString())
        .build();
  }

  private final WalletClient wallet;
  private final KeycloakClient keycloak;
  private final PidIssuerClient pidIssuer;
  private final WalletAttestationIssuerClient attestationIssuer;
  private final String audience;
  private final String clientId;

  private IssuanceAgent(Builder builder) {
    this.wallet = builder.wallet;
    this.keycloak = builder.keycloak;
    this.pidIssuer = builder.pidIssuer;
    this.attestationIssuer = builder.attestationIssuer;
    this.audience = builder.audience;
    this.clientId = builder.clientId;
  }

  public static class Builder {
    private WalletAttestationIssuerClient attestationIssuer = new WalletAttestationIssuerClient();
    private WalletClient wallet = new InternalWalletClient(attestationIssuer);
    private KeycloakClient keycloak = new KeycloakClient();
    private PidIssuerClient pidIssuer = new PidIssuerClient();
    private String audience = ServiceIdentifier.PID_ISSUER.toString();
    private String clientId = DEFAULT_CLIENT_ID;

    public Builder wallet(WalletClient wallet) {
      this.wallet = wallet;
      return this;
    }

    public Builder keycloak(KeycloakClient keycloak) {
      this.keycloak = keycloak;
      return this;
    }

    public Builder pidIssuer(PidIssuerClient pidIssuer) {
      this.pidIssuer = pidIssuer;
      return this;
    }

    public Builder attestationIssuer(WalletAttestationIssuerClient attestationIssuer) {
      this.attestationIssuer = attestationIssuer;
      return this;
    }

    public Builder audience(String audience) {
      this.audience = audience;
      return this;
    }

    public Builder clientId(String clientId) {
      this.clientId = clientId;
      return this;
    }

    public IssuanceAgent build() {
      return new IssuanceAgent(this);
    }
  }

  public String issuePidCredential(ECKey bindingKey, String username, String password)
      throws Exception {
    ECKey encryptionKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();

    String accessToken;
    if (DEFAULT_CLIENT_ID.equals(clientId)) {
      String wia = attestationIssuer.getWalletInstanceAttestation(bindingKey);
      accessToken =
          keycloak.getDpopAccessTokenWithClientAttestation(
              "pid-issuer-realm",
              clientId,
              bindingKey,
              wia,
              Map.of(
                  "grant_type", "password",
                  "username", username,
                  "password", password,
                  "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                  "role", "user"));
    } else {
      accessToken =
          keycloak.getDpopAccessToken(
              "pid-issuer-realm",
              bindingKey,
              Map.of(
                  "grant_type", "password",
                  "client_id", clientId,
                  "username", username,
                  "password", password,
                  "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                  "role", "user"));
    }

    String nonce = pidIssuer.getNonce(accessToken, bindingKey);
    String walletAttestation = wallet.createKeyAttestation(bindingKey, nonce);
    String proof = createProof(bindingKey, walletAttestation, nonce);
    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();
    Map<String, Object> payloadJson =
        pidIssuer
            .issueCredentials(
                accessToken, bindingKey, encryptionKey, proof,
                pidIssuerCredentialRequestEncryptionKey)
            .toJSONObject();

    return extractSdJwtVc(payloadJson);
  }

  private String createProof(ECKey jwk, String keyAttestation, String nonce) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .keyID(DEFAULT_KA_KEY_ID)
            .customParam("key_attestation", keyAttestation)
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
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
