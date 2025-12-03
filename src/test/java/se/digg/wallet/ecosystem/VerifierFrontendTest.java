// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

class VerifierFrontendTest {

  private final VerifierFrontendClient verifierFrontendClient = new VerifierFrontendClient();

  @Test
  void returnsVerifierStatus() {
    Response response = verifierFrontendClient.getVerifierStatus();
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.jsonPath().getString("status"), is("online"));
  }

  @Test
  void createsVerificationRequest() {
    VerifierPresentationResponse response = verifierFrontendClient.createPresentationRequest();
    assertNotNull(response);
    assertThat(response.transaction_id(), notNullValue());
    assertThat(response.request_uri(), notNullValue());
    assertThat(response.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @Test
  void returnsVerificationStatusForValidTransaction() {
    VerifierPresentationResponse presentationResponse =
        verifierFrontendClient.createPresentationRequest();
    String transactionId = presentationResponse.transaction_id();

    Response response = verifierFrontendClient.getPresentationStatus(transactionId);
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getBody().jsonPath().get("status"), is("pending"));
  }
}
