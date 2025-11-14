// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class KeycloakTest {

  private final KeycloakClient keycloak = new KeycloakClient();

  @ParameterizedTest
  @ValueSource(strings = {"live", "ready", "started", ""})
  @DisabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_HEALTH",
      matches = "true")
  void isHealthy(String path) {
    keycloak.tryGetHealth(path)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", equalTo("UP"));
  }

  @Test
  void servesPidIssuerRealm() {
    keycloak.tryGetRealm("pid-issuer-realm")
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("realm", equalTo("pid-issuer-realm"));
  }

  @Test
  void canGetDpopAccessTokenForClientCredentials() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();

    assertNotNull(keycloak.getDpopAccessToken("pid-issuer-realm", jwk, Map.of(
        "grant_type", "client_credentials",
        "client_id", "pid-issuer-srv",
        "client_secret", "zIKAV9DIIIaJCzHCVBPlySgU8KgY68U2")));
  }

  @Test
  void canGetDpopAccessTokenForUser() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();

    assertNotNull(keycloak.getDpopAccessToken("pid-issuer-realm", jwk, Map.of(
        "grant_type", "password",
        "client_id", "wallet-dev",
        "username", "tneal",
        "password", "password")));
  }

  @Test
  void isAccessibleOnAlternateUrl() {
    new KeycloakClient(URI.create("https://localhost/.well-known/oauth-authorization-server/idp/"))
        .tryGetRealm("pid-issuer-realm")
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", equalTo("https://localhost/idp/realms/pid-issuer-realm"));
  }
}
