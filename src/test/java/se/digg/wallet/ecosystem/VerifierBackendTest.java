// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifierBackendTest {

  private static final Map<String, Object> body =
      Map.of(
          "type",
          "vp_token",
          "vp_token_type",
          "sd-jwt",
          "jar_mode",
          "by_value",
          "nonce",
          UUID.randomUUID().toString(),
          "dcql_query",
          Map.of(
              "credentials",
              java.util.List.of(
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
              java.util.List.of(
                  Map.of(
                      "options",
                      java.util.List.of(java.util.List.of("32f54163-7166-48f1-93d8-ff217bdb0653")),
                      "purpose",
                      "We need to verify your identity"))));
  private VerifierBackendClient verifierBackendClient;
  private IssuanceHelper issuanceHelper;

  @BeforeEach
  void setUp() {
    verifierBackendClient = new VerifierBackendClient();
    issuanceHelper = new IssuanceHelper();
  }

  @Test
  void isHealthy() {
    verifierBackendClient.isHealthy();
  }

  @Test
  void createVerificationRequest_shouldReturnValidResponse() {
    VerifierBackendTransactionResponse verifierRequestResponse =
        verifierBackendClient.createVerificationRequest(body);

    assertNotNull(verifierRequestResponse);
    assertThat(verifierRequestResponse.transaction_id(), notNullValue());
    assertThat(verifierRequestResponse.request(), notNullValue());
  }

  @Test
  void getVerificationEvents_shouldReturnValidResponse() {
    VerifierBackendTransactionResponse verificationRequest =
        verifierBackendClient.createVerificationRequest(body);
    String transactionId = verificationRequest.transaction_id();
    Response response = verifierBackendClient.getVerificationEvents(transactionId);
    assertThat(response.getStatusCode(), is(200));
  }

  @Test
  void validateSdJwtVcUtilityEndpoint_shouldReturnValidResponse() throws Exception {
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
    String nonce = UUID.randomUUID().toString();

    // 3. Create Key Binding JWT
    String vpToken =
        issuanceHelper.createVpToken(
            sdJwtVc, bindingKey, nonce, VerifierBackendClient.VERIFIER_AUDIENCE);

    // 4. Validate SD-JWT VC using the utility endpoint
    Response validationResponse =
        verifierBackendClient.validateSdJwtVc(vpToken, nonce, issuanceHelper.getIssuerChain());
    assertThat(validationResponse.getStatusCode(), is(200));
  }
}
