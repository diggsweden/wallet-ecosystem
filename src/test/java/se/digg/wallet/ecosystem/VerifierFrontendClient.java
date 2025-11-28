// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.Map;

public class VerifierFrontendClient {

  private final URI base;

  public VerifierFrontendClient() {
    this(ServiceIdentifier.VERIFIER_FRONTEND.getResourceRoot());
  }

  public VerifierFrontendClient(URI base) {
    this.base = base;
  }

  public VerifierFrontendRequestResponse createVerificationRequest() {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "type",
                "vp_token",
                "dcql_query",
                Map.of("credentials", new Object[] {}),
                "nonce",
                "nonce"))
        .when()
        .post("api/verifier-request")
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierFrontendRequestResponse.class);
  }

  public Response getVerificationStatus(String transactionId) {
    return given()
        .baseUri(base.toString())
        .when()
        .get("api/verifier-status/{transactionId}", transactionId)
        .then()
        .extract()
        .response();
  }

  public Response getVerifierStatus() {
    return given()
        .baseUri(base.toString())
        .when()
        .get("api/verifier-status")
        .then()
        .extract()
        .response();
  }
}
