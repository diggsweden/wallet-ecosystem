// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
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
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class VerifierBackendTest {
  private static final String dcqlId = UUID.randomUUID().toString();
  private VerifierBackendClient verifierBackend;
  private IssuanceHelper issuanceHelper;

  @BeforeEach
  void setUp() {
    verifierBackend = new VerifierBackendClient();
    issuanceHelper = new IssuanceHelper();
  }

  @Test
  @DisabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_VERIFIER_BACKEND_HEALTH",
      matches = "true")
  void isHealthy() {
    verifierBackend
        .tryGetHealth()
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", equalTo("UP"));
  }

  @Test
  void createsPresentationRequest() {
    VerifierPresentationResponse presentationResponse =
        verifierBackend.createPresentationRequestByValue(dcqlId);

    assertNotNull(presentationResponse);
    assertThat(presentationResponse.transaction_id(), notNullValue());
    assertThat(presentationResponse.request(), notNullValue());
    assertThat(presentationResponse.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @Test
  void returnsPresentationEvents() {
    VerifierPresentationResponse presentationResponse =
        verifierBackend.createPresentationRequestByValue(dcqlId);
    String transactionId = presentationResponse.transaction_id();
    Response response = verifierBackend.getPresentationEvents(transactionId);

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
        VerifiablePresentationToken.asString(sdJwtVc, bindingKey, nonce);

    // 3. Validate SD-JWT VC using the utility endpoint
    verifierBackend
        .validateSdJwtVc(vpToken, nonce)
        .then()
        .assertThat().statusCode(200)
        .and().body("vct", is("urn:eudi:pid:1"))
        .and().body("iss", is(ServiceIdentifier.PID_ISSUER.toString()))
        .and().body("family_name", is("Neal"))
        .and().body("issuing_authority", is("SE Administrative authority"));
  }

  @Test
  @EnabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_INCLUDE_TESTS_WITH_UNTRUSTED_ISSUER",
      matches = "true")
  void rejectsUntrustedPidIssuer() throws Exception {
    IssuanceHelper untrustedIssuer = IssuanceHelper.untrusted();

    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    // 1. Get credential
    String sdJwtVc = untrustedIssuer.issuePidCredential(bindingKey, "tneal", "password");

    // 2. Create Key Binding JWT
    String nonce = UUID.randomUUID().toString();
    String vpToken =
        VerifiablePresentationToken.asString(sdJwtVc, bindingKey, nonce);

    // 3. Validate SD-JWT VC using the utility endpoint
    verifierBackend.validateSdJwtVc(vpToken, nonce)
        .then()
        .assertThat().statusCode(400)
        .and().body(".", hasSize(1))
        .and().body("[0].error", is("IssuerCertificateIsNotTrusted"))
        .and().body("[0].description", is("sd-jwt vc issuer certificate is not trusted"));
  }
}
