// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.Map;

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

  public void isHealthy() {
    given()
        .baseUri(base.toString())
        .when()
        .get("actuator/health")
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", is("UP"));
  }

  public VerifierBackendTransactionResponse createVerificationRequest(Map<String, Object> body) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("ui/presentations")
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierBackendTransactionResponse.class);
  }

  public String getRequestBody(String nonce, String issuerChain, String dcqlId) {
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

  public VerifierBackendTransactionByReferenceResponse createVerificationRequestByReference(
      String nonce, String issuerChain, String dcqlId) {

    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body(getRequestBody(nonce, issuerChain, dcqlId))
        .when()
        .post("ui/presentations")
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierBackendTransactionByReferenceResponse.class);
  }

  public Response postWalletResponse(String responseUri, String state, String vpToken) {
    return given()
        .baseUri(responseUri)
        .contentType(ContentType.URLENC)
        .formParam("state", state)
        .formParam("vp_token", vpToken)
        .when()
        .post()
        .then()
        .extract()
        .response();
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

  public Response getVerificationStatus(String transactionId) {
    return given()
        .baseUri(base.toString())
        .when()
        .get("ui/presentations/{transactionId}", transactionId)
        .then()
        .extract()
        .response();
  }

  public Response getVerificationEvents(String transactionId) {
    return given()
        .baseUri(base.toString())
        .when()
        .get("ui/presentations/{transactionId}/events", transactionId)
        .then()
        .extract()
        .response();
  }
}
