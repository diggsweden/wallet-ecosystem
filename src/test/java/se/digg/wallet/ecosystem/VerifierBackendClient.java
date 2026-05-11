// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class VerifierBackendClient {

  public static final String VERIFIER_AUDIENCE =
      Optional.ofNullable(System.getenv("DIGG_WALLET_ECOSYSTEM_VERIFIER_AUDIENCE"))
          .orElse("x509_san_dns:localhost");

  private static boolean ready = false;

  private final URI base;

  public VerifierBackendClient() {
    this(ServiceIdentifier.VERIFIER_BACKEND.getResourceRoot());
  }

  public VerifierBackendClient(URI base) {
    this.base = base;
  }

  /**
   * Waits for the Verifier Backend service to be fully responsive. This ensures that the service is
   * reachable through the reverse proxy and that the internal components are initialized and ready.
   * The result is cached for the duration of the JVM session.
   */
  public static void waitUntilReady() {
    if (ready) {
      return;
    }
    VerifierBackendClient client = new VerifierBackendClient();
    await("Wait for Verifier Backend to be healthy")
        .atMost(60, SECONDS)
        .pollInterval(Duration.ofSeconds(2))
        .ignoreExceptions()
        .untilAsserted(() -> {
          client.tryGetHealth()
              .then()
              .assertThat().statusCode(200)
              .and().body("status", is("UP"));
        });
    ready = true;
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
      String nonce, String dcqlId) {

    return postPresentation(getRequestBodyByReference(nonce, dcqlId));
  }

  public String getRequestBodyByReference(String nonce, String dcqlId) {
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
                "jar_mode": "by_reference"
            }
            """,
        dcqlId, dcqlId, nonce);
  }

  public Response tryValidateSdJwtVc(String sdJwtVc, String nonce) {
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
