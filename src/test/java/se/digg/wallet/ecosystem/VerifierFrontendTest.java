// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

@DisplayNameGeneration(DisplayNameGenerator.Standard.class)
class VerifierFrontendTest {

  static final List<String> SITE_NAMES = List.of("vaccincentralen", "matcentralen");
  private final VerifierFrontendClient verifierFrontend = new VerifierFrontendClient();

  @Test
  void isHealthy() {
    verifierFrontend.tryGetHome()
        .then()
        .assertThat().statusCode(200)
        .and().body(containsString("demoapplikationer"));
  }

  @Test
  void returnsVerifierStatus() {
    Response response = verifierFrontend.getVerifierStatus();
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.jsonPath().getString("status"), is("online"));
  }

  @ParameterizedTest
  @FieldSource("SITE_NAMES")
  void createsVerificationRequest(String siteName) {
    VerifierPresentationResponse response = verifierFrontend.createPresentationRequest(siteName);
    assertNotNull(response);
    assertThat(response.transaction_id(), notNullValue());
    assertThat(response.request_uri(), notNullValue());
    assertThat(response.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @ParameterizedTest
  @FieldSource("SITE_NAMES")
  void returnsVerificationStatusForValidTransaction(String siteName) {
    VerifierPresentationResponse presentationResponse =
        verifierFrontend.createPresentationRequest(siteName);
    String transactionId = presentationResponse.transaction_id();

    Response response = verifierFrontend.getPresentationStatus(siteName, transactionId);
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getBody().jsonPath().get("status"), is("pending"));
  }
}
