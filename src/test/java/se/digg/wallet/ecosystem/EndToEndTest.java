// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class EndToEndTest {

  private final VerifierBackendClient verifierBackend = new VerifierBackendClient();
  private final IssuanceHelper issuanceHelper = new IssuanceHelper();

  @Test
  void getCredential() throws Exception {
    // 1. Initialize transaction
    String issuerChain = issuanceHelper.getIssuerChain();
    String nonce = UUID.randomUUID().toString();
    String dcqlId = UUID.randomUUID().toString();
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
                        dcqlId,
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
                        List.of(List.of(dcqlId)),
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
    String responseUri = signedAuthRequest.getJWTClaimsSet().getStringClaim("response_uri");

    // 3. Get credential
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    ECKey encryptionKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();

    String sdJwtVc = issuanceHelper.issuePidCredential(bindingKey, encryptionKey);

    // 4. Create vp_token
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(sdJwtVc.getBytes(StandardCharsets.US_ASCII));
    String sdHash = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

    JWTClaimsSet kbJwtClaims =
        new JWTClaimsSet.Builder()
            .audience("x509_san_dns:refimpl-verifier-backend.wallet.local")
            .issueTime(Date.from(Instant.now()))
            .claim("nonce", nonce)
            .claim("sd_hash", sdHash)
            .build();
    SignedJWT kbJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .jwk(bindingKey.toPublicJWK())
                .type(new JOSEObjectType("kb+jwt"))
                .build(),
            kbJwtClaims);
    kbJwt.sign(new ECDSASigner(bindingKey));
    String kbJwtSerialized = kbJwt.serialize();
    String vpToken =
        sdJwtVc.endsWith("~") ? sdJwtVc + kbJwtSerialized : sdJwtVc + "~" + kbJwtSerialized;

    // 5. Post wallet response
    String vpTokenJson = String.format("{ \"%s\": [ \"%s\" ] }", dcqlId, vpToken);
    Response postWalletResponse =
        verifierBackend.postWalletResponse(responseUri, state, vpTokenJson);
    assertThat(postWalletResponse.getStatusCode(), is(200));

    // 6. Check verification status and events
    Response verificationStatusResponse = verifierBackend.getVerificationStatus(transactionId);
    assertThat(verificationStatusResponse.getStatusCode(), is(200));

    Response verificationEventsResponse = verifierBackend.getVerificationEvents(transactionId);
    assertThat(verificationEventsResponse.getStatusCode(), is(200));
  }
}
