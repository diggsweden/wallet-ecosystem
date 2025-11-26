// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  public static Stream<Arguments> pidIssuerRealmMetadataUrls() {
    return Stream.of(MetadataLocationStrategy.values()).map(s -> Arguments.of(
        s.toString(),
        s.applyTo(
            ServiceIdentifier.KEYCLOAK.getResourceRoot().resolve("realms/pid-issuer-realm"),
            "/.well-known/oauth-authorization-server")));
  }

  @ParameterizedTest
  @MethodSource("pidIssuerRealmMetadataUrls")
  void servesMetadataForPidIssuerRealm(
      String labelNotUsedInTestButIncludedInDisplayName, URI uri) {
    given().when().get(uri)
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", equalTo(ServiceIdentifier.KEYCLOAK.getResourceRoot().resolve(
            "realms/pid-issuer-realm").toString()))
        .and().body("token_endpoint", equalTo(ServiceIdentifier.KEYCLOAK.getResourceRoot().resolve(
            "realms/pid-issuer-realm/protocol/openid-connect/token").toString()));
  }
}
