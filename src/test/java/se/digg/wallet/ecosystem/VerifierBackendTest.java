// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifierBackendTest {
  private static final String dcqlId = UUID.randomUUID().toString();
  private VerifierBackendClient verifierBackendClient;
  private IssuanceHelper issuanceHelper;

  public String getRequestBody(String dcqlId) {
    return String.format(
        """
            {
                "dcql_query": {
                    "credentials": [ {
                            "format": "dc+sd-jwt",
                            "vct": "urn:eudi:pid:1",
                            "id": "%s",
                            "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" }
                    }],
                    "credential_sets": [ {
                            "purpose": "We need to verify your identity",
                            "options": [["%s"]]
                    }]
                },
                "nonce": "%s",
                "vp_token_type": "sd-jwt",
                "type": "vp_token",
                "jar_mode": "by_value"
            }
            """,
        dcqlId, dcqlId, UUID.randomUUID());
  }

  @BeforeEach
  void setUp() {
    verifierBackendClient = new VerifierBackendClient();
    issuanceHelper = new IssuanceHelper();
  }

  @Test
  void isHealthy() {
    verifierBackendClient
        .tryGetHealth()
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", equalTo("UP"));
  }

  @Test
  void createVerificationRequest() {
    VerifierBackendTransactionResponse verifierRequestResponse =
        verifierBackendClient.createVerificationRequest(getRequestBody(dcqlId));

    assertNotNull(verifierRequestResponse);
    assertThat(verifierRequestResponse.transaction_id(), notNullValue());
    assertThat(verifierRequestResponse.request(), notNullValue());
    assertThat(verifierRequestResponse.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @Test
  void returnsVerificationEvents() {
    VerifierBackendTransactionResponse verificationRequest =
        verifierBackendClient.createVerificationRequest(getRequestBody(dcqlId));
    String transactionId = verificationRequest.transaction_id();
    Response response = verifierBackendClient.getVerificationEvents(transactionId);

    assertThat(response.getStatusCode(), is(200));
    List<String> events = response.jsonPath().getList("events.event");
    assertThat(events, is(List.of("Transaction initialized")));
  }

  @Test
  void acceptsValidSdJwtVcCredential() throws Exception {
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    // 1. Get credential
    String sdJwtVc = issuanceHelper.issuePidCredential(bindingKey, "tneal", "password");
    String nonce = UUID.randomUUID().toString();

    // 2. Create Key Binding JWT
    String vpToken =
        issuanceHelper.createVpToken(
            sdJwtVc, bindingKey, nonce, VerifierBackendClient.VERIFIER_AUDIENCE);

    // 3. Validate SD-JWT VC using the utility endpoint
    verifierBackendClient
        .validateSdJwtVc(vpToken, nonce, issuanceHelper.getIssuerChain())
        .then()
        .assertThat().statusCode(200)
        .and().body("vct", is("urn:eudi:pid:1"))
        .and().body("iss", is("https://localhost/pid-issuer"))
        .and().body("family_name", is("Neal"))
        .and().body("issuing_authority", is("SE Administrative authority"));
  }
}
