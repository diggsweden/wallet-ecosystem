// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;

public class WalletClientGatewayClient {

  private final URI base = ServiceIdentifier.WALLET_CLIENT_GATEWAY.getResourceRoot();

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  @Deprecated
  public String createAccountByOidc(String postBody, String oidcSession) {
    return given()
        .when().contentType(ContentType.JSON).body(postBody)
        .header("SESSION", oidcSession)
        .cookie("SESSION", oidcSession)
        .post(base.resolve("oidc/accounts/v1"))
        .then()
        .assertThat().statusCode(201)
        .and().body("accountId", not(blankOrNullString()))
        .extract().body().jsonPath().getString("accountId");
  }

  public String createAccountByApiKey(String postBody, String apiKey, String path) {
    return given()
        .when()
        .contentType(ContentType.JSON).body(postBody)
        .header("X-API-KEY", apiKey)
        .post(base.resolve(path))
        .then()
        .assertThat().statusCode(201)
        .and().body("accountId", not(blankOrNullString()))
        .extract().body().jsonPath().getString("accountId");
  }

  public String initChallenge(String accountId, String keyId) {
    return given()
        .get(
            base.resolve("public/auth/session/challenge?accountId=%s&keyId=%s"
                .formatted(accountId, keyId)))
        .then()
        .assertThat().statusCode(200).and()
        .extract().body().jsonPath().getString("nonce");
  }

  public String respondToChallenge(String signedJwt) {
    return given()
        .when().contentType(ContentType.JSON).body("""
            {
              "signedJwt": "%s"
            }""".formatted(signedJwt))
        .post(base.resolve("public/auth/session/response"))
        .then()
        .assertThat().statusCode(200)
        .and().body("sessionId", not(blankOrNullString()))
        // Deprecated header
        .and().and().header("session", not(blankOrNullString()))
        .extract().body().jsonPath().get("sessionId");
  }

  public String createAttributeAttestation(String sessionId, String postBody) {
    return given().when().contentType(ContentType.JSON).body(postBody)
        .header("Session", sessionId)
        .post(base.resolve("attribute-attestations"))
        .then()
        .assertThat().statusCode(201).and()
        .body("id", not(emptyString()))
        .extract().body().jsonPath().getString("id");
  }

  public Response getAttributeAttestation(String sessionId, String id) {
    return given()
        .when()
        .header("Session", sessionId)
        .get(base.resolve("attribute-attestations/%s".formatted(id)));
  }

  public Response createWalletUnitAttestation(String sessionId, String nonce) {
    RequestSpecification request = given()
        .when()
        .contentType(ContentType.JSON)
        .header("session", sessionId);

    if (nonce != null) {
      request.queryParam("nonce", nonce);
    }

    return request.post(base.resolve("wua"));
  }
}
