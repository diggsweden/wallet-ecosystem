// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
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

  public VerifierPresentationResponse createPresentationRequest() {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body("""
            {
                "dcql_query": {
                    "credentials": [ {
                            "format": "dc+sd-jwt",
                            "vct": "urn:eudi:pid:1",
                            "id": "query_0",
                            "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" }
                    }],
                    "credential_sets": [ {
                            "purpose": "We need to verify your identity",
                            "options": [["query_0"]]
                    }]
                },
                "nonce": "nonce",
                "type": "vp_token"
            }
            """)
        .when()
        .post("api/verifier-request")
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierPresentationResponse.class);
  }

  public Response getPresentationStatus(String transactionId) {
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
