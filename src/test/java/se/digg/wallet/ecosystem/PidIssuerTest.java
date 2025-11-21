// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class PidIssuerTest {
  private final PidIssuerClient pidIssuer = new PidIssuerClient();
  private final KeycloakClient keycloak = new KeycloakClient();

  @Test
  void presentsUsefulLinks() {
    Map<String, String> linksByLabel = pidIssuer.getUsefulLinks();

    assertThat(linksByLabel.keySet(), containsInAnyOrder(
        "Credential Issuer Metadata",
        "Authorization Server Metadata",
        "SD-JWT VC Issuer Metadata",
        "PID SD-JWT VC Type Metadata"));

    linksByLabel.forEach((label, link) -> {
      given().when().get(link).then().assertThat().statusCode(200);
    });
  }

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

    // 1. Get access token for user
    String accessToken = keycloak.getDpopAccessToken("pid-issuer-realm", userJwk,
        Map.of(
            "grant_type", "password",
            "client_id", "wallet-dev",
            "username", "tneal",
            "password", "password",
            "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
            "role", "user"));

    // 2. Get nonce
    String nonce = pidIssuer.getNonce(accessToken, userJwk);

    assertNotNull(nonce);
  }
}
