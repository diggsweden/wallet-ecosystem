// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
import io.restassured.response.Response;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class EndToEndTest {

  private final KeycloakClient keycloak = new KeycloakClient();
  private final WalletProviderClient walletProvider = new WalletProviderClient();
  private final PidIssuerClient pidIssuer = new PidIssuerClient();
  private final VerifierBackendClient verifierBackend = new VerifierBackendClient();

  @Test
  void getCredential() throws Exception {
    // 1. Initialize transaction
    String issuerChain = getIssuerChain();
    String nonce = UUID.randomUUID().toString();
    Map<String, Object> requestBody =
        Map.of(
            "type",
            "vp_token",
            "vp_token_type",
            "sd-jwt",
            "jar_mode",
            "by_reference",
            "nonce",
            nonce,
            "issuer_chain",
            issuerChain,
            "dcql_query",
            Map.of(
                "credentials",
                List.of(
                    Map.of(
                        "id",
                        "32f54163-7166-48f1-93d8-ff217bdb0653",
                        "format",
                        "dc+sd-jwt",
                        "vct",
                        "urn:eudi:pid:1",
                        "meta",
                        Map.of("doctype_value", "eu.europa.ec.eudi.pid.1"))),
                "credential_sets",
                List.of(
                    Map.of(
                        "options",
                        List.of(List.of("32f54163-7166-48f1-93d8-ff217bdb0653")),
                        "purpose",
                        "We need to verify your identity"))));
    VerifierBackendTransactionByReferenceResponse transactionResponse =
        verifierBackend.createVerificationRequestByReference(requestBody);
    String transactionId = transactionResponse.transaction_id();
    String requestUri = transactionResponse.request_uri();

    // 2. Get authorization request
    Response authRequestResponse = verifierBackend.getAuthorizationRequest(requestUri);
    String authRequest = authRequestResponse.body().asString();
    SignedJWT signedAuthRequest = SignedJWT.parse(authRequest);
    String state = signedAuthRequest.getJWTClaimsSet().getStringClaim("state");

    // 3. Get credential
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();
    String accessToken =
        keycloak.getDpopAccessToken(
            "pid-issuer-realm",
            userJwk,
            Map.of(
                "grant_type",
                "password",
                "client_id",
                "wallet-dev",
                "username",
                "tneal",
                "password",
                "password",
                "scope",
                "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                "role",
                "user"));
    ECKey jwk =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();
    String walletAttestation = walletProvider.getWalletUnitAttestation(jwk);
    String cnonce = pidIssuer.getNonce(accessToken, userJwk);
    String proof = createProof(jwk, walletAttestation, cnonce);
    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();
    Map<String, Object> payloadJson =
        pidIssuer
            .issueCredentials(
                accessToken, userJwk, jwk, proof, pidIssuerCredentialRequestEncryptionKey)
            .toJSONObject();

    String sdJwtVc = extractSdJwtVc(payloadJson);

    // 4. Create vp_token
    JWTClaimsSet kbJwtClaims =
        new JWTClaimsSet.Builder()
            .audience(verifierBackend.getName())
            .issueTime(new Date())
            .claim("nonce", nonce)
            .build();
    SignedJWT kbJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .jwk(jwk.toPublicJWK())
                .type(new JOSEObjectType("kb+jwt"))
                .build(),
            kbJwtClaims);
    kbJwt.sign(new ECDSASigner(jwk));
    String kbJwtSerialized = kbJwt.serialize();
    String vpToken = sdJwtVc + "~" + kbJwtSerialized;

    // 5. Post wallet response
    String dcqlId = "32f54163-7166-48f1-93d8-ff217bdb0653";
    String vpTokenJson = String.format("{ \"%s\": [ \"%s\" ] }", dcqlId, vpToken);
    Response postWalletResponse = verifierBackend.postWalletResponse(state, vpTokenJson);
    assertThat(postWalletResponse.getStatusCode(), is(200));
  }

  @Test
  void validateSdJwtVcUtilityEndpoint_shouldReturnValidResponse() throws Exception {
    // 1. Get credential
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
    ECKey jwk =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();
    String walletAttestation = walletProvider.getWalletUnitAttestation(jwk);
    String nonce = pidIssuer.getNonce(accessToken, userJwk);
    String proof = createProof(jwk, walletAttestation, nonce);
    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();
    Map<String, Object> payloadJson =
        pidIssuer
            .issueCredentials(
                accessToken, userJwk, jwk, proof, pidIssuerCredentialRequestEncryptionKey)
            .toJSONObject();

    String sdJwtVc = extractSdJwtVc(payloadJson);

    // 2. Validate SD-JWT VC using the utility endpoint
    Response validationResponse = verifierBackend.validateSdJwtVc(sdJwtVc, nonce, getIssuerChain());
    assertThat(validationResponse.getStatusCode(), is(200));
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
            .audience(pidIssuer.getName())
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
    Object firstCredential = credentials.get(0);
    if (firstCredential instanceof String) {
      return (String) firstCredential;
    } else if (firstCredential instanceof Map) {
      Map<String, Object> credentialMap = (Map<String, Object>) firstCredential;
      if (credentialMap.containsKey("sd_jwt")) {
        return (String) credentialMap.get("sd_jwt");
      } else if (credentialMap.containsKey("credential")) {
        return (String) credentialMap.get("credential");
      } else {
        throw new IllegalStateException(
            "Could not find SD-JWT VC in credential map: " + credentialMap);
      }
    } else {
      throw new IllegalStateException("Unexpected credential type: " + firstCredential.getClass());
    }
  }

  private String getIssuerChain() throws IOException {
    return new String(
        java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("config/issuer/issuer_cert.pem")));
  }
}
