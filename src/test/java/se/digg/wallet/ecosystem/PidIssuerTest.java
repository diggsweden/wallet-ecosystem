// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PidIssuerTest {

  private static final ServiceIdentifier IDENTIFIER = ServiceIdentifier.PID_ISSUER;

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
  }

  public static Stream<Arguments> usefulLinks() {
    return new PidIssuerClient().getUsefulLinks().entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

  public static Stream<Arguments> authorizationServers() {
    return new PidIssuerClient().getAuthorizationServers().stream()
        .map(s -> Arguments.of("Authorization Server", s.toString()));
  }

  @ParameterizedTest
  @MethodSource("usefulLinks")
  @MethodSource("authorizationServers")
  void linkWorks(String labelNotUsedInTestButIncludedInDisplayName, String link) {
    given().when().get(link).then().assertThat().statusCode(200);
  }

  public static Stream<Arguments> credentialIssuerMetadataUrls() {
    return Stream.of(MetadataLocationStrategy.values()).map(s -> Arguments.of(
        s.toString(),
        s.applyTo(IDENTIFIER.toUri(), "/.well-known/openid-credential-issuer")));
  }

  @ParameterizedTest
  @MethodSource("credentialIssuerMetadataUrls")
  void servesCredentialIssuerMetadata(
      String labelNotUsedInTestButIncludedInDisplayName, URI uri) {

    given()
        .when()
        .get(uri)
        .then()
        .assertThat().statusCode(200)
        .and().body(
            "credential_issuer",
            is(IDENTIFIER.toString()))
        .and().body(
            "credential_request_encryption.jwks.keys",
            not(empty()))
        .and().body(
            "authorization_servers",
            hasItem(ServiceIdentifier.KEYCLOAK.getResourceRoot().resolve(
                "realms/pid-issuer-realm").toString()));
  }

  public static Stream<Arguments> jwtVcIssuerMetadataUrls() {
    return Stream.of(MetadataLocationStrategy.values()).map(s -> Arguments.of(
        s.toString(),
        s.applyTo(IDENTIFIER.toUri(), "/.well-known/jwt-vc-issuer")));
  }

  @ParameterizedTest
  @MethodSource("jwtVcIssuerMetadataUrls")
  void servesJwtVcIssuerMetadata(
      String labelNotUsedInTestButIncludedInDisplayName, URI uri) {

    given()
        .when()
        .get(uri)
        .then()
        .assertThat().statusCode(200)
        .and().body("issuer", is(IDENTIFIER.toString()))
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
