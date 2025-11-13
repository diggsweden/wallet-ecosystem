// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.notNullValue;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

public class KeycloakClient {

  private final URI base;

  public KeycloakClient() {
    this(URI.create(
        Optional.ofNullable(System.getenv("DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI"))
            .orElse("https://localhost/idp") + "/"));
  }

  public KeycloakClient(URI base) {
    this.base = base;
  }

  public String getDpopAccessToken(
      String realm, ECKey key, Map<String, String> parameters) throws JOSEException {

    URI tokenEndpoint = base.resolve("realms/" + realm + "/protocol/openid-connect/token");
    String dpopProof = DpopUtil.createDpopProof(key, tokenEndpoint.toString(), "POST");

    return given()
        .when()
        .contentType(ContentType.URLENC)
        .header("DPoP", dpopProof)
        .formParams(parameters)
        .post(tokenEndpoint)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue())
        .body("token_type", org.hamcrest.CoreMatchers.equalTo("DPoP"))
        .extract()
        .path("access_token");
  }

  public Response tryGetHealth(String path) {
    return given().when().get(base.resolve("health/" + path));
  }

  public Response tryGetRealm(String name) {
    return given().when().get(base.resolve("realms/" + name));
  }
}
