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

class VerifierBackendTest {

  private VerifierBackendClient verifierBackendClient;

  @BeforeEach
  void setUp() {

    verifierBackendClient = new VerifierBackendClient();
  }

  @Test
  void isHealthy() {

    verifierBackendClient.isHealthy();
  }

  @Test
  void createVerificationRequest_shouldReturnValidResponse() {

    VerifierBackendTransactionResponse verifierRequestResponse =
        verifierBackendClient.createVerificationRequest();

    assertNotNull(verifierRequestResponse);

    assertThat(verifierRequestResponse.transaction_id(), notNullValue());

    assertThat(verifierRequestResponse.request(), notNullValue());
  }

  @Test
  void getVerificationStatus_shouldReturnValidResponse() {

    VerifierBackendTransactionResponse verificationRequest =
        verifierBackendClient.createVerificationRequest();

    String transactionId = verificationRequest.transaction_id();

    Response response = verifierBackendClient.getVerificationStatus(transactionId);

    assertThat(response.getStatusCode(), is(400));
  }

  @Test
  void getVerificationEvents_shouldReturnValidResponse() {

    VerifierBackendTransactionResponse verificationRequest =
        verifierBackendClient.createVerificationRequest();

    String transactionId = verificationRequest.transaction_id();

    Response response = verifierBackendClient.getVerificationEvents(transactionId);

    assertThat(response.getStatusCode(), is(200));
  }
}
