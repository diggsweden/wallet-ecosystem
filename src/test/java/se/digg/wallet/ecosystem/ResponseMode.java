// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

public enum ResponseMode {
  DIRECT_POST("direct_post") {
    @Override
    public Response postWalletResponse(
        String responseUri,
        SignedJWT signedAuthRequest,
        String state,
        String dcqlId,
        String vpToken) {
      String vpTokenJson = String.format("{ \"%s\": [ \"%s\" ] }", dcqlId, vpToken);
      return given()
          .baseUri(responseUri)
          .contentType(ContentType.URLENC)
          .formParam("state", state)
          .formParam("vp_token", vpTokenJson)
          .when()
          .post()
          .then()
          .extract()
          .response();
    }
  },

  DIRECT_POST_JWT("direct_post.jwt") {
    @Override
    public Response postWalletResponse(
        String responseUri,
        SignedJWT signedAuthRequest,
        String state,
        String dcqlId,
        String vpToken)
        throws Exception {
      String responseJwt = DirectPostJwtPayload.create(signedAuthRequest, state, dcqlId, vpToken);
      return given()
          .baseUri(responseUri)
          .contentType(ContentType.URLENC)
          .formParam("response", responseJwt)
          .when()
          .post()
          .then()
          .extract()
          .response();
    }
  };

  private final String value;

  ResponseMode(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public abstract Response postWalletResponse(
      String responseUri,
      SignedJWT signedAuthRequest,
      String state,
      String dcqlId,
      String vpToken)
      throws Exception;

  @Override
  public String toString() {
    return value;
  }
}
