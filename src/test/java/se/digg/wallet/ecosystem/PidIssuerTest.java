// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.path.json.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
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
        "PID SD-JWT VC Type Metadata",
        "Learning Credential SD-JWT VC Type Metadata",
        "Protected Resource Metadata"));
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
    given().urlEncodingEnabled(false).when().get(link).then().assertThat().statusCode(200);
  }

  @ParameterizedTest
  @EnumSource(MetadataLocationStrategy.class)
  void servesCredentialIssuerMetadata(MetadataLocationStrategy strategy) {
    String response = pidIssuer.tryGetOpenIdCredentialIssuerMetadata(strategy)
        .then()
        .assertThat().statusCode(200)
        .extract().asString();

    if (response.split("\\.").length == 3) {
      response = new String(Base64.getUrlDecoder().decode(response.split("\\.")[1]),
          StandardCharsets.UTF_8);
    }

    JsonPath jp = new JsonPath(response);
    assertThat(jp.getString("credential_issuer"), is(IDENTIFIER.toString()));
    assertThat(jp.getList("credential_request_encryption.jwks.keys"), is(not(empty())));
    assertThat(jp.getList("authorization_servers"), hasItem(ServiceIdentifier.KEYCLOAK
        .getResourceRoot().resolve("realms/pid-issuer-realm").toString()));
  }

  @Test
  void servesMetadataWithLogo() {
    String response =
        pidIssuer.tryGetOpenIdCredentialIssuerMetadata(MetadataLocationStrategy.OID4VCI_COMPLIANT)
            .then()
            .assertThat().statusCode(200)
            .extract().asString();

    if (response.split("\\.").length == 3) {
      response = new String(Base64.getUrlDecoder().decode(response.split("\\.")[1]),
          StandardCharsets.UTF_8);
    }

    JsonPath jp = new JsonPath(response);
    assertThat(jp.getList("display"), hasSize(1));
    var uri = jp.getString("display[0].logo.uri");

    given().get(uri).then().statusCode(200);
  }

  @ParameterizedTest
  @EnumSource(MetadataLocationStrategy.class)
  void servesJwtVcIssuerMetadata(MetadataLocationStrategy strategy) {
    pidIssuer.tryGetJwtVcIssuerMetadata(strategy)
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
