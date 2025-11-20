// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class VerifierFrontendClient {

  private final String name;
  private final URI base;

  public VerifierFrontendClient(String baseUri) {
    this.name = baseUri;
    this.base = URI.create(baseUri + "/");
  }

  public VerifierFrontendClient() {
    name =
        Optional.ofNullable(System.getenv("DIGG_WALLET_ECOSYSTEM_VERIFIER_BASE_URI"))
            .orElse("https://localhost/custom-verifier");
    base = URI.create(name + "/");
  }

  public String getName() {
    return name;
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

  public URI getBaseUri() {
    return base;
  }
}
