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

  private static final String DEFAULT_WUA_KEY_ID = "0";

  static IssuanceAgent untrusted() {
    return new IssuanceAgent(
        new InternalWalletClient(
            new WalletProviderClient(
                ServiceIdentifier.UNTRUSTED_WALLET_PROVIDER.getResourceRoot())),
        new KeycloakClient(ServiceIdentifier.UNTRUSTED_KEYCLOAK.getResourceRoot()),
        new PidIssuerClient(ServiceIdentifier.UNTRUSTED_PID_ISSUER.getResourceRoot()),
        new WalletProviderClient(
            ServiceIdentifier.UNTRUSTED_WALLET_PROVIDER.getResourceRoot()),
        ServiceIdentifier.UNTRUSTED_PID_ISSUER.toString(),
        DEFAULT_CLIENT_ID);
  }

  private final WalletClient wallet;
  private final KeycloakClient keycloak;
  private final PidIssuerClient pidIssuer;
  private final WalletProviderClient walletProvider;
  private final String audience;
  private final String clientId;

  public IssuanceAgent() {
    this(new InternalWalletClient(new WalletProviderClient()));
  }

  public IssuanceAgent(WalletClient wallet) {
    this(wallet, DEFAULT_CLIENT_ID);
  }

  public IssuanceAgent(WalletClient wallet, String clientId) {
    this(
        wallet,
        new KeycloakClient(),
        new PidIssuerClient(),
        new WalletProviderClient(),
        ServiceIdentifier.PID_ISSUER.toString(),
        clientId);
  }

  public IssuanceAgent(
      WalletClient wallet,
      KeycloakClient keycloak,
      PidIssuerClient pidIssuer,
      String audience) {
    this(
        wallet,
        keycloak,
        pidIssuer,
        new WalletProviderClient(),
        audience,
        DEFAULT_CLIENT_ID);
  }

  public IssuanceAgent(
      WalletClient wallet,
      KeycloakClient keycloak,
      PidIssuerClient pidIssuer,
      WalletProviderClient walletProvider,
      String audience,
      String clientId) {

    this.wallet = wallet;
    this.keycloak = keycloak;
    this.pidIssuer = pidIssuer;
    this.walletProvider = walletProvider;
    this.audience = audience;
    this.clientId = clientId;
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
      String wia = walletProvider.getWalletInstanceAttestation(bindingKey);
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

  private String createProof(ECKey jwk, String wua, String nonce) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .keyID(DEFAULT_WUA_KEY_ID)
            .customParam("key_attestation", wua)
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(clientId)
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
