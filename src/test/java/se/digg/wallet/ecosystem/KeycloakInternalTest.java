// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK_INTERNAL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

@DisabledIfEnvironmentVariable(
    named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_INTERNAL",
    matches = "true")
class KeycloakInternalTest {

  private static KeycloakClient clientFor(ServiceIdentifier serviceIdentifier) {
    return new KeycloakClient(serviceIdentifier.getResourceRoot());
  }

  @Test
  void pidIssuerRealmIsAccessibleInternally() {
    clientFor(KEYCLOAK_INTERNAL)
        .tryGetRealm("pid-issuer-realm")
        .then()
        .assertThat().statusCode(anyOf(is(200)));
  }

  @Test
  void masterRealmIsAccessibleInternally() {
    clientFor(KEYCLOAK_INTERNAL).tryGetRealm("master").then().assertThat().statusCode(200);
  }

  @Test
  void adminConsoleIsAccessibleInternally() {
    given()
        .when()
        .get(KEYCLOAK_INTERNAL.getResourceRoot().resolve("admin/master/console/"))
        .then()
        .assertThat().statusCode(200);
  }

  @Test
  void adminConsoleLoadsCorrectly() {
    given()
        .when()
        .get(KEYCLOAK_INTERNAL.getResourceRoot().resolve("admin/master/console/"))
        .then()
        .assertThat().statusCode(200)
        .and().contentType(containsString("text/html"))
        .and().body(containsString("<title>Keycloak Administration Console</title>"))
        .and().body(containsString("id=\"app\""))
        .and().body(containsString("rel=\"icon\""));
  }
}
