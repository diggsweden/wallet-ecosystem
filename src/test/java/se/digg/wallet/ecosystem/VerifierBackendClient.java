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
import java.util.Optional;
import java.util.UUID;

public class VerifierBackendClient {

  private final String name;
  private final URI base;

  public VerifierBackendClient(String baseUri) {
    this.name = baseUri;
    this.base = URI.create(baseUri + "/");
  }

  public VerifierBackendClient() {
    name =
        Optional.ofNullable(System.getenv("DIGG_WALLET_ECOSYSTEM_VERIFIER_BACKEND_BASE_URI"))
            .orElse("https://localhost/refimpl-verifier-backend");
    base = URI.create(name + "/");
  }

  public String getName() {
    return name;
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

  public VerifierBackendTransactionByReferenceResponse createVerificationRequestByReference(
      Map<String, Object> body) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.JSON)
        .body(body)
        .when()
        .post("ui/presentations")
        .then()
        .statusCode(200)
        .extract()
        .as(VerifierBackendTransactionByReferenceResponse.class);
  }

  public Response getAuthorizationRequest(String requestUri) {
    return given()
        .baseUri(requestUri)
        .when()
        .get()
        .then()
        .extract()
        .response();
  }

  public Response postWalletResponse(String state, String vpToken) {
    return given()
        .baseUri(base.toString())
        .contentType(ContentType.URLENC)
        .formParam("state", state)
        .formParam("vp_token", vpToken)
        .when()
        .post("wallet/direct_post")
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

  public URI getBaseUri() {
    return base;
  }
}
