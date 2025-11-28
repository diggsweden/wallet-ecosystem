// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifierFrontendClientTest {

  private VerifierFrontendClient verifierClient;

  @BeforeEach
  void setUp() {
    verifierClient = new VerifierFrontendClient();
  }

  @Test
  void getVerifierStatus_shouldReturn200() {
    Response response = verifierClient.getVerifierStatus();
    assertThat(response.getStatusCode(), is(200));
  }

  @Test
  void createVerificationRequest_shouldReturnValidResponse() {
    VerifierFrontendRequestResponse verifierFrontendRequestResponse =
        verifierClient.createVerificationRequest();
    assertNotNull(verifierFrontendRequestResponse);
    assertThat(verifierFrontendRequestResponse.transaction_id(), notNullValue());
    assertThat(verifierFrontendRequestResponse.request_uri(), notNullValue());
  }

  @Test
  void getVerificationStatus_withValidTransactionId_shouldReturn200() {
    // First, create a verification request to get a transaction ID
    VerifierFrontendRequestResponse verifierFrontendRequestResponse =
        verifierClient.createVerificationRequest();
    String transactionId = verifierFrontendRequestResponse.transaction_id();

    // Then, get the status of that transaction
    Response response = verifierClient.getVerificationStatus(transactionId);
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getBody().jsonPath().get("status"), is("pending"));
  }

  @Test
  void getVerificationStatus_withInvalidTransactionId_shouldReturn200WithPendingStatus() {
    Response response = verifierClient.getVerificationStatus("invalid-transaction-id");
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getBody().jsonPath().get("status"), is("pending"));
  }
}
