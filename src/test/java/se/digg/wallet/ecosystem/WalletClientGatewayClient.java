// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;

public class WalletClientGatewayClient {

  private final URI base = ServiceIdentifier.WALLET_CLIENT_GATEWAY.getResourceRoot();

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  public String createAccount(String postBody, String oidcSession) {
    return given()
        .when().contentType(ContentType.JSON).body(postBody)
        .header("SESSION", oidcSession)
        .cookie("SESSION", oidcSession)
        .post(base.resolve("oidc/accounts/v1"))
        .then()
        .assertThat()
        .statusCode(201).and()
        .extract()
        .body()
        .jsonPath()
        .getString("accountId");
  }

  public String initChallenge(String accountId, String keyId) {
    return given()
        .get(
            base.resolve("public/auth/session/challenge?accountId=%s&keyId=%s"
                .formatted(accountId, keyId)))
        .then()
        .assertThat().statusCode(200).and()
        .extract()
        .body()
        .jsonPath()
        .getString("nonce");
  }

  public String respondToChallenge(String signedJwt) {
    return given()
        .when().contentType(ContentType.JSON).body("""
            {
              "signedJwt": "%s"
            }""".formatted(signedJwt))
        .post(base.resolve("public/auth/session/response"))
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .response()
        .getHeaders()
        .getValue("session");
  }



  public String createAttributeAttestation(String sessionId, String postBody)
      throws Exception {
    return given().when().contentType(ContentType.JSON).body(postBody)
        .header("Session", sessionId)
        .post(base.resolve("attribute-attestations"))
        .then()
        .assertThat().statusCode(201).and()
        .extract()
        .body()
        .jsonPath()
        .getString("id");
  }

  public Response getAttributeAttestation(String sessionId, String id) {
    return given()
        .when()
        .header("Session", sessionId)
        .get(base.resolve("attribute-attestations/%s".formatted(id)));
  }

  @Deprecated(forRemoval = true)
  public Response createWalletUnitAttestation(String sessionId, String postBody) throws Exception {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(postBody)
        .header("session", sessionId)
        .post(base.resolve("wua/v2"));
  }

  public Response createWalletUnitAttestationV3(String sessionId, String nonce) throws Exception {
    String wuaUrl = "wua/v3"
        + (nonce != null ? "?nonce=" + nonce : "");

    return given()
        .when()
        .contentType(ContentType.JSON)
        .header("session", sessionId)
        .post(base.resolve(wuaUrl));
  }
}
