// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PidIssuerTest {
  public static final String TOKEN_ENDPOINT =
      "https://localhost/idp/realms/pid-issuer-realm/protocol/openid-connect/token";
  private static final String PID_ISSUER_NONCE_URL =
      "https://localhost/pid-issuer/wallet/nonceEndpoint";

  @ParameterizedTest
  @ValueSource(strings = {
      "https://localhost/pid-issuer/.well-known/openid-credential-issuer",
      "https://localhost/.well-known/openid-credential-issuer/pid-issuer"
  })
  void servesOpenIdCredentialIssuerMetadata(String url) {
    given()
        .when()
        .get(url)
        .then()
        .assertThat().statusCode(200)
        .and().body(
            "credential_issuer",
            is("https://localhost/pid-issuer"))
        .and().body(
            "credential_request_encryption.jwks.keys",
            not(empty()))
        .and().body(
            "authorization_servers",
            hasItem("https://localhost/idp/realms/pid-issuer-realm"));
  }

  @Test
  void servesJwtVcIssuerMetadata() {
    given()
        .when()
        .get("https://localhost/.well-known/jwt-vc-issuer/pid-issuer")
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", is("https://localhost/pid-issuer"))
        .and().body("jwks.keys", not(empty()));
  }

  @Test
  void getNonce() throws Exception {
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();
    String dpopProof = DpopUtil.createDpopProof(userJwk, TOKEN_ENDPOINT, "POST");

    // 1. Get access token for user
    String accessToken =
        given()
            .when()
            .contentType(ContentType.URLENC)
            .header("DPoP", dpopProof)
            .formParam("grant_type", "password")
            .formParam("client_id", "wallet-dev")
            .formParam("username", "tneal")
            .formParam("password", "password")
            .formParam("scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt")
            .formParam("role", "user")
            .post(TOKEN_ENDPOINT)
            .then()
            .assertThat()
            .statusCode(200)
            .and()
            .body("access_token", notNullValue())
            .body("token_type", org.hamcrest.CoreMatchers.equalTo("DPoP"))
            .extract()
            .path("access_token");

    // 2. Get nonce
    String nonce =
        given()
            .auth()
            .oauth2(accessToken)
            .header("DPoP", DpopUtil.createDpopProof(userJwk, PID_ISSUER_NONCE_URL, "POST"))
            .when()
            .post(PID_ISSUER_NONCE_URL)
            .then()
            .assertThat()
            .statusCode(200)
            .extract()
            .path("c_nonce");

    assertNotNull(nonce);
  }
}
