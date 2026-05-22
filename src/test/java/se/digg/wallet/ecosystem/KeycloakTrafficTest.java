// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK_INTERNAL;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK_EXTERNAL;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.response.Response;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class KeycloakTrafficTest {

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Throwable;
  }

  private static KeycloakClient clientFor(ServiceIdentifier serviceIdentifier) {
    return new KeycloakClient(serviceIdentifier.getResourceRoot());
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current;
  }

  private static void runWithInternalHostTolerance(
      ServiceIdentifier serviceIdentifier,
      ThrowingRunnable assertion) {
    try {
      assertion.run();
    } catch (Throwable ex) {
      if (serviceIdentifier == KEYCLOAK_INTERNAL && rootCause(ex) instanceof UnknownHostException) {
        Assumptions.assumeTrue(false,
            "Skipping KEYCLOAK_INTERNAL test: host is not resolvable outside DIGG network");
      }
      if (ex instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (ex instanceof Error error) {
        throw error;
      }
      throw new RuntimeException(ex);
    }
  }

  static Stream<ServiceIdentifier> allKeycloaks() {
    return Stream.of(KEYCLOAK, KEYCLOAK_INTERNAL, KEYCLOAK_EXTERNAL);
  }

  static Stream<Arguments> masterRealmAccessCases() {
    return Stream.of(
        // positive: master realm accessible via Traefik internal routing
        Arguments.of(KEYCLOAK, is(200)),
        // positive: master realm accessible via direct internal access
        Arguments.of(KEYCLOAK_INTERNAL, is(200)),
        // negative: master realm blocked from external url
        Arguments.of(KEYCLOAK_EXTERNAL, anyOf(is(403), is(404))));
  }

  static Stream<Arguments> adminConsoleAccessCases() {
    return Stream.of(
        // positive: admin console accessible via Traefik internal routing
        Arguments.of(KEYCLOAK, is(200)),
        // positive: admin console accessible via direct internal access
        Arguments.of(KEYCLOAK_INTERNAL, is(200)),
        // negative: admin console blocked from external url
        Arguments.of(KEYCLOAK_EXTERNAL, anyOf(is(403), is(404))));
  }

  static Stream<Arguments> pidIssuerRealmAccessCases() {
    return Stream.of(
        // positive: pid-issuer-realm accessible via Traefik
        Arguments.of(KEYCLOAK, is(200)),
        // positive: pid-issuer-realm accessible via internal endpoint
        Arguments.of(KEYCLOAK_INTERNAL, is(200)),
        // positive: pid-issuer-realm accessible from external (whitelisted path)
        Arguments.of(KEYCLOAK_EXTERNAL, is(200)));
  }

  static Stream<Arguments> allKeycloaksAndStrategies() {
    return allKeycloaks()
        .flatMap(serviceIdentifier -> Arrays.stream(MetadataLocationStrategy.values())
            .map(strategy -> Arguments.of(serviceIdentifier, strategy)));
  }

  private static URI resolveExternalResourceUri(String resourceReference) {
    if (resourceReference.startsWith("http://") || resourceReference.startsWith("https://")) {
      return URI.create(resourceReference);
    }
    return KEYCLOAK_EXTERNAL.getResourceRoot().resolve(resourceReference);
  }

  @ParameterizedTest
  @ValueSource(strings = {"live", "ready", "started", ""})
  @DisabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_KEYCLOAK_HEALTH",
      matches = "true")
  void isHealthy(String path) {
    clientFor(KEYCLOAK).tryGetHealth(path)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", equalTo("UP"));
  }

  @ParameterizedTest
  @MethodSource("allKeycloaks")
  void servesRealm(ServiceIdentifier serviceIdentifier) {
    runWithInternalHostTolerance(serviceIdentifier,
        () -> clientFor(serviceIdentifier).tryGetRealm("pid-issuer-realm")
            .then()
            .assertThat()
            .statusCode(200)
            .and()
            .body("realm", equalTo("pid-issuer-realm")));
  }

  @Test
  void canGetDpopAccessTokenForClientCredentials() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();

    assertNotNull(clientFor(KEYCLOAK).getDpopAccessToken("pid-issuer-realm", jwk, Map.of(
        "grant_type", "client_credentials",
        "client_id", "pid-issuer-srv",
        "client_secret", "zIKAV9DIIIaJCzHCVBPlySgU8KgY68U2")));
  }

  @Test
  void canGetDpopAccessTokenForUser() throws JOSEException {
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();

    assertNotNull(clientFor(KEYCLOAK).getDpopAccessToken("pid-issuer-realm", jwk, Map.of(
        "grant_type", "password",
        "client_id", "wallet-dev",
        "username", "tneal",
        "password", "password")));
  }

  @ParameterizedTest
  @MethodSource("allKeycloaksAndStrategies")
  void servesMetadataForPidIssuerRealm(ServiceIdentifier serviceIdentifier,
      MetadataLocationStrategy strategy) {
    runWithInternalHostTolerance(serviceIdentifier, () -> clientFor(serviceIdentifier)
        .tryGetOauthAuthorizationServerMetadata("pid-issuer-realm", strategy)
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", equalTo(serviceIdentifier.getResourceRoot().resolve(
            "realms/pid-issuer-realm").toString()))
        .and().body("token_endpoint", equalTo(serviceIdentifier.getResourceRoot().resolve(
            "realms/pid-issuer-realm/protocol/openid-connect/token").toString())));
  }

  @Test
  void pidIssuerRealmLoginFormLoadsExternally() {
    Response accountPage = clientFor(KEYCLOAK_EXTERNAL).tryGetRealmAccount("pid-issuer-realm");
    String html = accountPage
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .contentType(containsString("text/html"))
        .extract()
        .asString();

    java.util.regex.Matcher loginFormMatcher = Pattern
        .compile("<form[^>]*id=['\"]kc-form-login['\"]")
        .matcher(html);

    boolean hasLoginForm = loginFormMatcher.find();
    boolean hasAuthenticateAction = html.contains("/login-actions/authenticate");
    boolean hasAccountConsoleShell = html.contains("<title>Account Management</title>")
        || html.contains("keycloak__loading-container")
        || html.contains("id=\"app\"");
    String htmlPreview = html.substring(0, Math.min(html.length(), 800));

    assertTrue(
        (hasLoginForm && hasAuthenticateAction) || hasAccountConsoleShell,
        "Expected Keycloak login form or account shell HTML for pid-issuer-realm. First 800 chars: "
            + htmlPreview);

    java.util.regex.Matcher resourceMatcher = Pattern
        .compile("(?:href|src)\\s*=\\s*['\"]([^'\"]*/resources/[^'\"]+)['\"]")
        .matcher(html);

    if (resourceMatcher.find()) {
      URI resourceUri = resolveExternalResourceUri(resourceMatcher.group(1));
      given()
          .when()
          .get(resourceUri)
          .then()
          .assertThat()
          .statusCode(200);
    }
  }

  // Whitelist tests — verify Traefik blocks disallowed paths externally

  @ParameterizedTest
  @MethodSource("pidIssuerRealmAccessCases")
  void pidIssuerRealmIsAccessible(ServiceIdentifier serviceIdentifier,
      Matcher<Integer> expectedStatus) {
    runWithInternalHostTolerance(serviceIdentifier,
        () -> clientFor(serviceIdentifier).tryGetRealm("pid-issuer-realm")
            .then()
            .assertThat()
            .statusCode(expectedStatus)
            .and()
            .body("realm", equalTo("pid-issuer-realm")));
  }

  @ParameterizedTest
  @MethodSource("masterRealmAccessCases")
  void masterRealmAccess(ServiceIdentifier serviceIdentifier, Matcher<Integer> expectedStatus) {
    runWithInternalHostTolerance(serviceIdentifier,
        () -> clientFor(serviceIdentifier).tryGetRealm("master")
            .then()
            .assertThat()
            .statusCode(expectedStatus));
  }

  @ParameterizedTest
  @MethodSource("adminConsoleAccessCases")
  void adminConsoleAccess(ServiceIdentifier serviceIdentifier, Matcher<Integer> expectedStatus) {
    runWithInternalHostTolerance(serviceIdentifier, () -> given()
        .when()
        .get(serviceIdentifier.getResourceRoot().resolve("admin"))
        .then()
        .assertThat()
        .statusCode(expectedStatus));
  }
}
