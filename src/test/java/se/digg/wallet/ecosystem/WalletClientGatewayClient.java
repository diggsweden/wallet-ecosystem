// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;

public class WalletClientGatewayClient {

  private final URI base = ServiceIdentifier.WALLET_CLIENT_GATEWAY.getResourceRoot();
  private static final String API_KEY = Property.WALLET_CLIENT_GATEWAY_API_KEY.getValue();

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  public Response tryGetApiInfo() {
    return given()
        .when()
        .get(base.resolve("api-info"));
  }

  public Response tryGetClientConfig(String deviceOs, String appVersion) {
    return given()
        .when()
        .header("X-API-KEY", API_KEY)
        .header("Wallet-Device-OS", deviceOs)
        .header("Wallet-App-Version", appVersion)
        .get(base.resolve("util/v0/client-configs"));
  }

  public Response tryGetMaintenanceWindow() {
    return given()
        .when()
        .header("X-API-KEY", API_KEY)
        .get(base.resolve("util/v0/maintenance-windows"));
  }

  public String createAccount(String postBody) {
    return given()
        .when()
        .contentType(ContentType.JSON).body(postBody)
        .header("X-API-KEY", API_KEY)
        .post(base.resolve("v0/accounts"))
        .then()
        .assertThat().statusCode(201)
        .and().body("accountId", not(blankOrNullString()))
        .extract().body().jsonPath().getString("accountId");
  }

  public void addWalletKey(String sessionId, String keyBody) {
    given()
        .when()
        .contentType(ContentType.JSON)
        .body(keyBody)
        .header("Session", sessionId)
        .header("X-API-KEY", API_KEY)
        .post(base.resolve("v0/accounts/wallet-keys"))
        .then()
        .assertThat().statusCode(201);
  }

  public void addSecurityEnvelope(String sessionId, String keyBody) {
    given()
        .when()
        .contentType(ContentType.JSON)
        .body(keyBody)
        .header("Session", sessionId)
        .header("X-API-KEY", API_KEY)
        .post(base.resolve("v0/accounts/security-envelopes"))
        .then()
        .assertThat().statusCode(201);
  }

  public String initChallenge(String accountId, String keyId) {
    return given()
        .get(
            base.resolve("public/auth/session/challenge?accountId=%s&keyId=%s"
                .formatted(accountId, keyId)))
        .then()
        .assertThat().statusCode(200)
        .and().body("nonce", not(blankOrNullString()))
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
        .and().header("session", not(blankOrNullString()))
        .extract().body().jsonPath().get("sessionId");
  }

  public Response tryCreateWalletUnitAttestation(String sessionId, String nonce) {
    RequestSpecification request = given()
        .when()
        .contentType(ContentType.JSON)
        .header("session", sessionId)
        .header("X-API-KEY", API_KEY);

    if (nonce != null) {
      request.queryParam("nonce", nonce);
    }

    return request.post(base.resolve("wua"));
  }

  public Response saveDeviceState(String sessionId, String body) {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(body)
        .header("Session", sessionId)
        .header("X-API-KEY", API_KEY)
        .post(base.resolve("hsm/v0/device-states"));
  }

  public Response createHsmRequest(String sessionId, String body) {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(body)
        .header("Session", sessionId)
        .header("X-API-KEY", API_KEY)
        .post(base.resolve("hsm/v0/requests"));
  }

  public Response getAsyncHsmResult(String sessionId, String id) {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .header("Session", sessionId)
        .header("X-API-KEY", API_KEY)
        .get(base.resolve("hsm/v0/requests/%s".formatted(id)));
  }
}
