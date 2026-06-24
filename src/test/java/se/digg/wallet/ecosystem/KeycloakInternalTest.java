// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK_INTERNAL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIfEnvironmentVariable(
    named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_INTERNAL",
    matches = "true")
class KeycloakInternalTest {

  private static final KeycloakClient internalKeycloak =
      new KeycloakClient(KEYCLOAK_INTERNAL.getResourceRoot());

  @ParameterizedTest
  @ValueSource(strings = {
      "pid-issuer-realm",
      "master"
  })
  void allowsAccessToRealm(String realm) {
    internalKeycloak.tryGetRealm(realm)
        .then().assertThat().statusCode(is(200));
  }

  @Test
  void servesMasterAdminConsole() {
    internalKeycloak.tryGetMasterAdminConsole()
        .then().assertThat().statusCode(200)
        .and().contentType(containsString("text/html"))
        .and().body(containsString("<title>Keycloak Administration Console</title>"))
        .and().body(containsString("id=\"app\""))
        .and().body(containsString("rel=\"icon\""));
  }

  @ParameterizedTest
  @MethodSource("masterAdminConsoleUrls")
  void servesMasterAdminConsolePointingToInternalUrls(String url) {
    assertThat(url, is(KEYCLOAK_INTERNAL.toString()));
  }

  private static Stream<Arguments.ArgumentSet> masterAdminConsoleUrls()
      throws JsonProcessingException {
    String environment = internalKeycloak.tryGetMasterAdminConsole()
        .then().assertThat().statusCode(200)
        .extract().body().htmlPath().get("html.body.script.find { it.@id == 'environment' }");

    JsonNode root = new ObjectMapper().readTree(environment);
    assertThat(root.getNodeType(), is(JsonNodeType.OBJECT));
    return root.propertyStream()
        .map(entry -> Map.entry(entry.getKey(), entry.getValue().asText()))
        .filter(entry -> entry.getValue().startsWith("http"))
        .map(entry -> Arguments.argumentSet(entry.getKey(), entry.getValue()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"realms/master/console/", "realms/admin/master/console"})
  void masterRealmAndAdminConsoleIsBlockedInternally(String path) {
    given()
        .when().get(KEYCLOAK_INTERNAL.getResourceRoot().resolve(path))
        .then().assertThat().statusCode(anyOf(is(403), is(404)));
  }
}
