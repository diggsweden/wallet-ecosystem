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

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.path.json.JsonPath;
import java.time.Instant;
import java.util.Date;
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

  public static Stream<Arguments> usefulLinks() {
    return new PidIssuerClient()
        .getUsefulLinks().entrySet().stream()
        .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
  }

  public static Stream<Arguments> authorizationServers() {
    return new PidIssuerClient()
        .getAuthorizationServers().stream()
        .map(s -> Arguments.of("Authorization Server", s.toString()));
  }

  @Test
  void presentsUsefulLinks() {
    Map<String, String> linksByLabel = pidIssuer.getUsefulLinks();

    assertThat(
        linksByLabel.keySet(),
        containsInAnyOrder(
            "Credential Issuer Metadata",
            "Authorization Server Metadata",
            "SD-JWT VC Issuer Metadata",
            "PID SD-JWT VC Type Metadata",
            "Learning Credential SD-JWT VC Type Metadata",
            "Protected Resource Metadata"));
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
    String response = pidIssuer.getDecodedOpenIdCredentialIssuerMetadata(strategy);

    JsonPath jp = new JsonPath(response);
    assertThat(jp.getString("credential_issuer"), is(IDENTIFIER.toString()));
    assertThat(jp.getList("credential_request_encryption.jwks.keys"), is(not(empty())));
    assertThat(
        jp.getList("authorization_servers"),
        hasItem(
            ServiceIdentifier.KEYCLOAK
                .getResourceRoot()
                .resolve("realms/pid-issuer-realm")
                .toString()));
  }

  @Test
  void servesMetadataWithLogo() {
    String response =
        pidIssuer.getDecodedOpenIdCredentialIssuerMetadata(
            MetadataLocationStrategy.OID4VCI_COMPLIANT);

    JsonPath jp = new JsonPath(response);
    assertThat(jp.getList("display"), hasSize(1));
    var uri = jp.getString("display[0].logo.uri");

    given().get(uri).then().statusCode(200);
  }

  @ParameterizedTest
  @EnumSource(MetadataLocationStrategy.class)
  void servesJwtVcIssuerMetadata(MetadataLocationStrategy strategy) {
    pidIssuer
        .tryGetJwtVcIssuerMetadata(strategy)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("issuer", is(IDENTIFIER.toString()))
        .and()
        .body("jwks.keys", not(empty()));
  }

  @Test
  void getNonce() throws Exception {
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();

    // 1. Get access token for user
    String accessToken =
        keycloak.getDpopAccessToken(
            "pid-issuer-realm",
            userJwk,
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

  @Test
  void rejectsInvalidWuaKeyId() throws Exception {
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    String accessToken =
        keycloak.getDpopAccessToken(
            "pid-issuer-realm",
            bindingKey,
            Map.of(
                "grant_type", "password",
                "client_id", "wallet-dev",
                "username", "tneal",
                "password", "password",
                "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                "role", "user"));

    String nonce = pidIssuer.getNonce(accessToken, bindingKey);
    WalletClient wallet = new InternalWalletClient(new WalletProviderClient());
    String walletAttestation = wallet.createWalletUnitAttestation(bindingKey, nonce);

    // Create an invalid proof with a mismatched keyID ("invalid-kid" instead of "0")
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .keyID("invalid-kid")
            .customParam("key_attestation", walletAttestation)
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer("wallet-dev")
            .audience(IDENTIFIER.toString())
            .issueTime(Date.from(Instant.now()))
            .claim("nonce", nonce)
            .build();

    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(new ECDSASigner(bindingKey));
    String invalidProof = signedJwt.serialize();

    String credentialsEndpoint =
        IDENTIFIER.getResourceRoot().resolve("wallet/credentialEndpoint").toString();

    // Construct a plaintext payload for the credential request since we just want to test proof
    // rejection
    String requestPayload =
        String.format(
            """
                {
                  "format": "vc+sd-jwt",
                  "proofs": { "jwt": ["%s"] },
                  "credential_configuration_id": "eu.europa.ec.eudi.pid_vc_sd_jwt"
                }""",
            invalidProof);

    given()
        .header("Authorization", "DPoP " + accessToken)
        .header(
            "DPoP", DpopUtil.createDpopProof(bindingKey, credentialsEndpoint, "POST", accessToken))
        .contentType("application/json")
        .body(requestPayload)
        .when()
        .post(credentialsEndpoint)
        .then()
        .assertThat()
        .statusCode(400);
  }

  @Test
  void rejectsPlainBearerToken() throws Exception {
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();

    String accessToken =
        keycloak.getDpopAccessToken(
            "pid-issuer-realm",
            userJwk,
            Map.of(
                "grant_type", "password",
                "client_id", "wallet-dev",
                "username", "tneal",
                "password", "password",
                "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
                "role", "user"));

    String credentialsEndpoint =
        IDENTIFIER.getResourceRoot().resolve("wallet/credentialEndpoint").toString();

    // The endpoint should enforce DPoP and reject standard Bearer tokens
    given()
        .header("Authorization", "Bearer " + accessToken)
        .contentType("application/json")
        .body("{}")
        .when()
        .post(credentialsEndpoint)
        .then()
        .assertThat()
        .statusCode(401);
  }
}
