// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.UUID;

public class VerifierBackendClient {

  public static final String VERIFIER_AUDIENCE =
      "x509_san_dns:refimpl-verifier-backend.wallet.local";

  private final URI base;

  public VerifierBackendClient() {
    this(ServiceIdentifier.VERIFIER_BACKEND.getResourceRoot());
  }

  public VerifierBackendClient(URI base) {
    this.base = base;
  }

  public Response tryGetHealth() {
    return given().when().get(base.resolve("actuator/health"));
  }

  private VerifierPresentationResponse postPresentation(String body) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("ui/presentations")
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierPresentationResponse.class);
  }

  public VerifierPresentationResponse createPresentationRequestByValue(String dcqlId) {
    return postPresentation(getRequestBodyByValue(dcqlId));
  }

  public String getRequestBodyByValue(String dcqlId) {
    return String.format(
        """
            {
                "dcql_query": {
                    "credentials": [ {
                            "format": "dc+sd-jwt",
                            "vct": "urn:eudi:pid:1",
                            "id": "%s",
                            "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" }
                    }],
                    "credential_sets": [ {
                            "purpose": "We need to verify your identity",
                            "options": [["%s"]]
                    }]
                },
                "nonce": "%s",
                "vp_token_type": "sd-jwt",
                "type": "vp_token",
                "jar_mode": "by_value"
            }
            """,
        dcqlId, dcqlId, UUID.randomUUID());
  }

  public VerifierPresentationResponse createPresentationRequestByReference(
      String nonce, String issuerChain, String dcqlId) {

    return postPresentation(getRequestBodyByReference(nonce, issuerChain, dcqlId));
  }

  public String getRequestBodyByReference(String nonce, String issuerChain, String dcqlId) {
    return String.format(
        """
            {
                "dcql_query": {
                    "credentials": [ {
                            "format": "dc+sd-jwt",
                            "vct": "urn:eudi:pid:1",
                            "id": "%s",
                            "meta": { "doctype_value": "eu.europa.ec.eudi.pid.1" }
                    }],
                    "credential_sets": [ {
                            "purpose": "We need to verify your identity",
                            "options": [["%s"]]
                    }]
                },
                "nonce": "%s",
                "vp_token_type": "sd-jwt",
                "type": "vp_token",
                "jar_mode": "by_reference",
                "issuer_chain": "%s"
            }
            """,
        dcqlId, dcqlId, nonce, issuerChain);
  }

  public Response validateSdJwtVc(String sdJwtVc, String nonce, String issuerChain) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.URLENC)
        .formParam("sd_jwt_vc", sdJwtVc)
        .formParam("nonce", nonce)
        .formParam("issuer_chain", issuerChain)
        .when()
        .post("utilities/validations/sdJwtVc")
        .then()
        .extract()
        .response();
  }

  public Response getPresentationsStatus(String transactionId) {
    return given()
        .baseUri(base.toString())
        .when()
        .get("ui/presentations/{transactionId}", transactionId)
        .then()
        .extract()
        .response();
  }

  public Response getPresentationEvents(String transactionId) {
    return given()
        .baseUri(base.toString())
        .when()
        .get("ui/presentations/{transactionId}/events", transactionId)
        .then()
        .extract()
        .response();
  }
}
