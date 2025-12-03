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
  void getVerifierStatus_shouldReturn200() {
    Response response = verifierFrontendClient.getVerifierStatus();
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.jsonPath().getString("status"), is("online"));
  }

  @Test
  void createVerificationRequest_shouldReturnValidResponse() {
    VerifierFrontendRequestResponse verifierFrontendRequestResponse =
        verifierFrontendClient.createVerificationRequest();
    assertNotNull(verifierFrontendRequestResponse);
    assertThat(verifierFrontendRequestResponse.transaction_id(), notNullValue());
    assertThat(verifierFrontendRequestResponse.request_uri(), notNullValue());
    assertThat(
        verifierFrontendRequestResponse.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @Test
  void getVerificationStatus_withValidTransactionId_shouldReturn200() {
    // First, create a verification request to get a transaction ID
    VerifierFrontendRequestResponse verifierFrontendRequestResponse =
        verifierFrontendClient.createVerificationRequest();
    String transactionId = verifierFrontendRequestResponse.transaction_id();

    // Then, get the status of that transaction
    Response response = verifierFrontendClient.getVerificationStatus(transactionId);
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getBody().jsonPath().get("status"), is("pending"));
  }
}
