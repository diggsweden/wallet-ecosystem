// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifierBackendTest {
  private static final String dcqlId = UUID.randomUUID().toString();

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
    assertThat(verifierRequestResponse.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @Test
  void getVerificationEvents_shouldReturnValidResponse() {
    VerifierBackendTransactionResponse verificationRequest =
        verifierBackendClient.createVerificationRequest(body);
    String transactionId = verificationRequest.transaction_id();
    Response response = verifierBackendClient.getVerificationEvents(transactionId);

    assertThat(response.getStatusCode(), is(200));
    List<String> events = response.jsonPath().getList("events.event");
    assertThat(events, is(List.of("Transaction initialized")));
  }

  @Test
  void validateSdJwtVcUtilityEndpoint_shouldReturnValidResponse() throws Exception {
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    // 1. Get credential
    String sdJwtVc = issuanceHelper.issuePidCredentialForTylerNeal(bindingKey);
    String nonce = UUID.randomUUID().toString();

    // 2. Create Key Binding JWT
    String vpToken =
        issuanceHelper.createVpToken(
            sdJwtVc, bindingKey, nonce, VerifierBackendClient.VERIFIER_AUDIENCE);

    // 3. Validate SD-JWT VC using the utility endpoint
    Response validationResponse =
        verifierBackendClient.validateSdJwtVc(vpToken, nonce, issuanceHelper.getIssuerChain());
    assertThat(validationResponse.getStatusCode(), is(200));
    assertThat(validationResponse.jsonPath().getString("vct"), is("urn:eudi:pid:1"));
    assertThat(validationResponse.jsonPath().getString("iss"), is("https://localhost/pid-issuer"));
    assertThat(validationResponse.jsonPath().getString("family_name"), is("Neal"));
    assertThat(
        validationResponse.jsonPath().getString("issuing_authority"),
        is("SE Administrative authority"));
  }
}
