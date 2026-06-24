// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class KeycloakTest {

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

  @ParameterizedTest
  @ValueSource(strings = {"pid-issuer-realm"})
  void servesRealm(String name) {
    keycloak.tryGetRealm(name)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("realm", equalTo(name));
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

  @ParameterizedTest
  @EnumSource(MetadataLocationStrategy.class)
  void servesMetadataForPidIssuerRealm(MetadataLocationStrategy strategy) {
    keycloak.tryGetOauthAuthorizationServerMetadata("pid-issuer-realm", strategy)
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", equalTo(KEYCLOAK.getResourceRoot().resolve(
            "realms/pid-issuer-realm").toString()))
        .and().body("token_endpoint", equalTo(KEYCLOAK.getResourceRoot().resolve(
            "realms/pid-issuer-realm/protocol/openid-connect/token").toString()));
  }

  @Test
  void pidIssuerRealmServesAccountConsole() {
    keycloak.tryGetRealmAccount("pid-issuer-realm")
        .then()
        .assertThat().statusCode(200)
        .and().contentType(containsString("text/html"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "realms/pid-issuer-realm/protocol/openid-connect/3p-cookies/step1.html",
      "realms/pid-issuer-realm/protocol/openid-connect/3p-cookies/step2.html"
  })
  void pidIssuerRealmLoadsResourcesExternally(String path) {
    given().when()
        .get(KEYCLOAK.getResourceRoot().resolve(path))
        .then().assertThat().statusCode(200)
        .and().contentType(containsString("text/html"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "auth",
      "logout",
      "introspect",
      "userinfo",
      "revoke",
      "certs"
  })
  void masterRealmDeniesRiskyProtocolEndpointsExternally(String path) {
    given()
        .when().get(KEYCLOAK.getResourceRoot().resolve("realms/master/protocol/" + path))
        .then().assertThat().statusCode(anyOf(is(403), is(404)));
  }

  @Test
  void adminConsoleIsBlockedExternally() {
    keycloak.tryGetMasterAdminConsole()
        .then().assertThat().statusCode(anyOf(is(403), is(404)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin/master/console/"})
  void masterRealmAndAdminConsoleIsBlockedExternally(String path) {
    given()
        .when().get(KEYCLOAK.getResourceRoot().resolve(path))
        .then().assertThat().statusCode(anyOf(is(403), is(404)));
  }
}
