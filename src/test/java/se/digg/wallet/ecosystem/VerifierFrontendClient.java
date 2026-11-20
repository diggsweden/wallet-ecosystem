// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;

public class VerifierFrontendClient {

  private final URI base;

  public VerifierFrontendClient() {
    this(ServiceIdentifier.VERIFIER_FRONTEND.getResourceRoot());
  }

  public VerifierFrontendClient(URI base) {
    this.base = base;
  }

  public Response tryGetHome() {
    return given().when().get(base.toString());
  }

  public VerifierPresentationResponse createPresentationRequest(IntendedUse intendedUse,
      String siteName) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body("""
            {
              "dcql_query": {
                "credentials": [{
                  "format": "dc+sd-jwt",
                  "id": "query_0",
                  "meta": {"vct_values": ["urn:eudi:pid:1"]},
                  "claims": [{
                      "path": [
                        "family_name"
                      ]
                  }]
                }],
                "credential_sets": [{
                  "purpose": "We need to verify your identity",
                  "options": [["query_0"]]
                }]
              },
              "nonce": "nonce",
              "jar_mode": "by_value",
              "intended_use_id": "%s"
            }
            """.formatted(intendedUse.id()))
        .when()
        .post("api/{sitename}-request", siteName)
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierPresentationResponse.class);
  }

  public Response getPresentationStatus(String siteName, String transactionId) {
    return given()
        .baseUri(base.toString())
        .when()
        .get("api/verifier-status/{siteName}/{transactionId}", siteName, transactionId)
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
