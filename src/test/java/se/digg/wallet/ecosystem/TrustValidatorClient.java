// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.response.Response;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with the Trust Validator service.
 */
public class TrustValidatorClient {

  private static boolean ready = false;

  private final URI base;

  private static final String VALIDATION_ENDPOINT = "trust";

  public TrustValidatorClient() {
    this(ServiceIdentifier.TRUST_VALIDATOR.getResourceRoot());
  }

  public TrustValidatorClient(URI base) {
    this.base = base;
  }

  /**
   * Waits for the Trust Validator service to be fully responsive. This ensures that the service is
   * reachable through the reverse proxy and that the underlying DSS engine is initialized and ready
   * to handle requests. The result is cached for the duration of the JVM session.
   */
  public static void waitUntilReady() {
    if (ready) {
      return;
    }
    TrustValidatorClient client = new TrustValidatorClient();
    await("Wait for Trust Validator to be healthy and engine initialized")
        .atMost(300, SECONDS)
        .pollInterval(Duration.ofSeconds(5))
        .ignoreExceptions()
        .untilAsserted(() -> {
          client.tryGetHealth()
              .then()
              .assertThat().statusCode(200)
              .and().body("status", is("UP"));

          // Dummy validation forces engine initialization.
          // Using a valid placeholder certificate chain.
          client.tryValidateCertificateChain(
              List.of(
                  "MIICPTCCAYUCEwG2Qo57w/6YwE4GByqGSM44BAAwDQYJKoZIhvcNAQELBQAwFjEUMBIGA1UEAxML"
                      + "dGVzdC1pc3N1ZXIwHhcNMjQwMTAxMDAwMDAwWhcNMzQwMTAxMDAwMDAwWjAWMRQwEgYDVQQ"
                      + "DEwt0ZXN0LWlzc3VlcjBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABJ/o6lZ8xS7GZ5S/S5"
                      + "X5fM6h4pUf5V9o+JpW8F9m+ZgQ4V+jS5gQk9KjBvX5B+7YQ3Z2W9B+mB0J5B+J5B+J5B+A="),
              "PID")
              .then()
              .assertThat().statusCode(anyOf(is(200), is(400)));
        });
    ready = true;
  }

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  public Response tryValidateCertificateChain(List<String> certificateChain,
      String verificationContext) {
    Map<String, Object> body = Map.of(
        "chain", certificateChain,
        "verificationContext", verificationContext);

    return given()
        .baseUri(base.toString())
        .contentType("application/json")
        .body(body)
        .when()
        .post(VALIDATION_ENDPOINT);
  }
}
