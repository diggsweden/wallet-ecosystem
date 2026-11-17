// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VerifierBackendClient {

  public static final String VERIFIER_AUDIENCE = Property.VERIFIER_AUDIENCE.getValue();

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

  public VerifierPresentationResponse createPresentationRequestByValue(IntendedUse intendedUse,
      String vct,
      String claim) {
    return createPresentationRequestByValue(intendedUse, vct, claim, ResponseMode.DIRECT_POST);
  }

  public VerifierPresentationResponse createPresentationRequestByValue(IntendedUse intendedUse,
      String vct, String claim, ResponseMode responseMode) {
    var body = """
        {
          "dcql_query": {
            "credentials": [{
              "id": "credential",
              "format": "dc+sd-jwt",
              "meta": { "vct_values": ["%s"] },
              "claims": [
                { "path": ["%s"] }
              ]
            }]
          },
          "nonce": "%s",
          "jar_mode": "by_value",
          "response_mode": "%s",
          "intended_use_id": "%s"
        }
        """.formatted(vct, claim, UUID.randomUUID(), responseMode.getValue(), intendedUse.id());
    return postPresentation(body);
  }

  public VerifierPresentationResponse createPresentationRequestByValue(
      IntendedUse intendedUse, String dcqlId) {
    return createPresentationRequestByValue(intendedUse, dcqlId, ResponseMode.DIRECT_POST);
  }

  public VerifierPresentationResponse createPresentationRequestByValue(
      IntendedUse intendedUse, String dcqlId, ResponseMode responseMode) {
    return postPresentation(getRequestBodyByValue(intendedUse, dcqlId, responseMode));
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

  public String getRequestBodyByValue(IntendedUse intendedUse, String dcqlId) {
    return getRequestBodyByValue(intendedUse, dcqlId, ResponseMode.DIRECT_POST);
  }

  public String getRequestBodyByValue(IntendedUse intendedUse, String dcqlId,
      ResponseMode responseMode) {
    return String.format(
        """
            {
                "dcql_query": {
                    "credentials": [ {
                            "format": "dc+sd-jwt",
                            "id": "%s",
                            "meta": { "vct_values": ["urn:eudi:pid:1"] },
                            "claims": [
                                { "path": ["family_name"] },
                                { "path": ["given_name"] },
                                { "path": ["personal_administrative_number"] },
                                { "path": ["address", "street_address"] }
                            ]
                    }],
                    "credential_sets": [ {
                            "purpose": "We need to verify your identity",
                            "options": [["%s"]]
                    }]
                },
                "nonce": "%s",
                "jar_mode": "by_value",
                "response_mode": "%s",
                "intended_use_id": "%s"
            }
            """,
        dcqlId, dcqlId, UUID.randomUUID(), responseMode.getValue(), intendedUse.id());
  }

  public VerifierPresentationResponse createPresentationRequestByReference(
      IntendedUse intendedUse, String nonce, String dcqlId) {
    return createPresentationRequestByReference(intendedUse, nonce, dcqlId,
        ResponseMode.DIRECT_POST);
  }

  public VerifierPresentationResponse createPresentationRequestByReference(
      IntendedUse intendedUse, String nonce, String dcqlId, ResponseMode responseMode) {
    return postPresentation(getRequestBodyByReference(intendedUse, nonce, dcqlId, responseMode));
  }

  public String getRequestBodyByReference(IntendedUse intendedUse, String nonce, String dcqlId) {
    return getRequestBodyByReference(intendedUse, nonce, dcqlId, ResponseMode.DIRECT_POST);
  }

  public String getRequestBodyByReference(IntendedUse intendedUse, String nonce, String dcqlId,
      ResponseMode responseMode) {
    return String.format(
        """
            {
                "dcql_query": {
                    "credentials": [ {
                            "format": "dc+sd-jwt",
                            "id": "%s",
                            "meta": { "vct_values": ["urn:eudi:pid:1"] },
                            "claims": [
                                { "path": ["family_name"] },
                                { "path": ["given_name"] },
                                { "path": ["personal_administrative_number"] },
                                { "path": ["address", "street_address"] }
                            ]
                    }],
                    "credential_sets": [ {
                            "purpose": "We need to verify your identity",
                            "options": [["%s"]]
                    }]
                },
                "nonce": "%s",
                "jar_mode": "by_reference",
                "response_mode": "%s",
                "intended_use_id": "%s"
            }
            """,
        dcqlId, dcqlId, nonce, responseMode.getValue(), intendedUse.id());
  }

  public Response submitPresentation(SignedJWT request, ResponseMode responseMode,
      Map<String, List<String>> vpToken) throws Exception {
    var responseUri = URI.create(request.getJWTClaimsSet().getStringClaim("response_uri"));
    assertThat("Test will only call the configured verifier host",
        responseUri.getHost(), equalToIgnoringCase(base.getHost()));

    return given()
        .baseUri(responseUri.toString())
        .redirects().follow(false)
        .contentType(ContentType.URLENC)
        .formParams(responseMode.getFormParams(request, vpToken))
        .when().post();
  }

  public Response validateSdJwtVc(String sdJwtVc, String nonce) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.URLENC)
        .formParam("sd_jwt_vc", sdJwtVc)
        .formParam("nonce", nonce)
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
