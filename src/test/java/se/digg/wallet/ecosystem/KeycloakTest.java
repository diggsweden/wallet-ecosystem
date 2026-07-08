// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class KeycloakTest {

  private static final KeycloakClient keycloak = new KeycloakClient();

  @ParameterizedTest
  @ValueSource(strings = {"live", "ready", "started", ""})
  @DisabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_HEALTH",
      matches = "true")
  void isHealthy(String path) {
    keycloak.tryGetHealth(path)
        .then().assertThat().statusCode(is(200))
        .and().body("status", is("UP"));
  }

  @Test
  void servesPidIssuerRealm() {
    keycloak.tryGetRealm("pid-issuer-realm")
        .then().assertThat().statusCode(is(200))
        .and().body("realm", is("pid-issuer-realm"));
  }

  @Test
  void blocksMasterRealm() {
    keycloak.tryGetRealm("master")
        .then().assertThat().statusCode(is(404));
  }

  @Test
  void blocksMasterAdminConsole() {
    keycloak.tryGetMasterAdminConsole()
        .then().assertThat().statusCode(is(404));
  }

  @Test
  void createsDpopAccessTokenFromValidClientCredentials() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();

    assertNotNull(keycloak.getDpopAccessToken("pid-issuer-realm", jwk, Map.of(
        "grant_type", "client_credentials",
        "client_id", "pid-issuer-srv",
        "client_secret", "zIKAV9DIIIaJCzHCVBPlySgU8KgY68U2")));
  }

  @Test
  void createsDpopAccessTokenFromValidPasswordCredentials() throws JOSEException {
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
        .then().assertThat().statusCode(is(200))
        .and().body("issuer", is(KEYCLOAK.getResourceRoot().resolve(
            "realms/pid-issuer-realm").toString()))
        .and().body("token_endpoint", is(KEYCLOAK.getResourceRoot().resolve(
            "realms/pid-issuer-realm/protocol/openid-connect/token").toString()));
  }

  @Test
  void servesAccountConsoleForPidIssuerRealm() {
    keycloak.tryGetAccountConsoleForRealm("pid-issuer-realm")
        .then().assertThat().statusCode(is(200))
        .and().contentType(containsString("text/html"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "realms/pid-issuer-realm/protocol/openid-connect/3p-cookies/step1.html",
      "realms/pid-issuer-realm/protocol/openid-connect/3p-cookies/step2.html"
  })
  void servesOpenIdConnectResourcesForPidIssuerRealm(String path) {
    given().when().get(KEYCLOAK.getResourceRoot().resolve(path))
        .then().assertThat().statusCode(is(200))
        .and().contentType(containsString("text/html"));
  }

  @ParameterizedTest
  @MethodSource("pidIssuerRealmAccountConsoleResources")
  void servesLinkedResourcesOfPidIssuerRealmAccountConsole(String reference) {
    given().when().get(KEYCLOAK.getResourceRoot().resolve(reference))
        .then().assertThat().statusCode(is(200));
  }

  private static Stream<String> pidIssuerRealmAccountConsoleResources() {
    return keycloak.tryGetAccountConsoleForRealm("pid-issuer-realm")
        .then().assertThat().statusCode(is(200))
        .extract().body().htmlPath().getList(
            "html.head.'*'.collectMany { [it.@href, it.@src] }",
            String.class)
        .stream().filter(Objects::nonNull);
  }

  @ParameterizedTest
  @MethodSource("pidIssuerRealmAccountConsoleImports")
  void servesImportsOfPidIssuerRealmAccountConsole(String reference) {
    given().when().get(KEYCLOAK.getResourceRoot().resolve(reference))
        .then().assertThat().statusCode(is(200));
  }

  private static Stream<String> pidIssuerRealmAccountConsoleImports()
      throws JsonProcessingException {
    String importMap = keycloak.tryGetAccountConsoleForRealm("pid-issuer-realm")
        .then().assertThat().statusCode(is(200))
        .extract().body().htmlPath().get("html.head.script.find { it.@type == 'importmap' }");

    JsonNode root = new ObjectMapper().readTree(importMap);
    assertThat(root.getNodeType(), is(JsonNodeType.OBJECT));
    return root.get("imports").propertyStream()
        .map(entry -> entry.getValue().asText());
  }
}
