// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KeycloakClient {

  private final URI base;

  public KeycloakClient() {
    this(ServiceIdentifier.KEYCLOAK.getResourceRoot());
  }

  public KeycloakClient(URI base) {
    this.base = base;
  }

  public Response tryGetOauthAuthorizationServerMetadata(
      String realm, MetadataLocationStrategy strategy) {

    return given().when().get(strategy.applyTo(
        base.resolve("realms/" + realm),
        "/.well-known/oauth-authorization-server"));
  }

  public String getDpopAccessToken(
      String realm, ECKey key, Map<String, String> parameters) throws JOSEException {

    URI tokenEndpoint = base.resolve("realms/" + realm + "/protocol/openid-connect/token");
    String dpopProof = DpopUtil.createDpopProof(key, tokenEndpoint.toString(), "POST");

    return given()
        .when()
        .contentType(ContentType.URLENC)
        .header("DPoP", dpopProof)
        .formParams(parameters)
        .post(tokenEndpoint)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue())
        .body("token_type", equalTo("DPoP"))
        .extract()
        .path("access_token");
  }

  public String getChallenge(String realm) {
    URI challengeEndpoint = base.resolve("realms/" + realm + "/challenge");
    return given()
        .when()
        .post(challengeEndpoint)
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .path("attestation_challenge");
  }

  public String createClientAttestationPop(
      String realm, String clientId, ECKey walletKey, String challenge) throws JOSEException {
    String realmIssuer = base.resolve("realms/" + realm).toString();
    JWSHeader popHeader = new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("oauth-client-attestation-pop+jwt"))
        .build();

    JWTClaimsSet popClaims = new JWTClaimsSet.Builder()
        .issuer(clientId)
        .audience(List.of(realmIssuer))
        .claim("challenge", challenge)
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(Instant.now()))
        .build();

    SignedJWT popJwt = new SignedJWT(popHeader, popClaims);
    popJwt.sign(new ECDSASigner(walletKey));
    return popJwt.serialize();
  }

  public String getDpopAccessTokenWithClientAttestation(
      String realm,
      String clientId,
      ECKey key,
      String walletInstanceAttestation,
      Map<String, String> parameters)
      throws JOSEException {
    return getDpopAccessTokenWithClientAttestation(
        realm, clientId, key, key, walletInstanceAttestation, parameters);
  }

  public String getDpopAccessTokenWithClientAttestation(
      String realm,
      String clientId,
      ECKey dpopKey,
      ECKey walletKey,
      String walletInstanceAttestation,
      Map<String, String> parameters)
      throws JOSEException {

    String challenge = getChallenge(realm);
    String pop = createClientAttestationPop(realm, clientId, walletKey, challenge);

    return tryGetDpopAccessTokenWithClientAttestation(
        realm, clientId, dpopKey, walletInstanceAttestation, pop, parameters)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue())
        .body("token_type", equalTo("DPoP"))
        .extract()
        .path("access_token");
  }

  public Response tryGetDpopAccessTokenWithClientAttestation(
      String realm,
      String clientId,
      ECKey dpopKey,
      String walletInstanceAttestation,
      String clientAttestationPop,
      Map<String, String> parameters) {

    URI tokenEndpoint = base.resolve("realms/" + realm + "/protocol/openid-connect/token");
    String dpopProof;
    try {
      dpopProof = DpopUtil.createDpopProof(dpopKey, tokenEndpoint.toString(), "POST");
    } catch (JOSEException e) {
      throw new RuntimeException(e);
    }

    Map<String, String> allParams = new HashMap<>(parameters);
    allParams.put("client_id", clientId);

    var request = given()
        .contentType(ContentType.URLENC)
        .header("DPoP", dpopProof);

    if (walletInstanceAttestation != null) {
      request.header("OAuth-Client-Attestation", walletInstanceAttestation);
    }
    if (clientAttestationPop != null) {
      request.header("OAuth-Client-Attestation-PoP", clientAttestationPop);
    }

    return request
        .formParams(allParams)
        .when()
        .post(tokenEndpoint);
  }

  public String getAccessToken(String realm, Map<String, String> parameters) {
    URI tokenEndpoint = base.resolve("realms/" + realm + "/protocol/openid-connect/token");

    return given()
        .when()
        .contentType(ContentType.URLENC)
        .formParams(parameters)
        .post(tokenEndpoint)
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("access_token", notNullValue())
        .extract()
        .path("access_token");
  }

  public Response introspectToken(String realm, String token, String clientId,
      String clientSecret) {
    URI introspectionEndpoint =
        base.resolve("realms/" + realm + "/protocol/openid-connect/token/introspect");
    return given()
        .auth().preemptive().basic(clientId, clientSecret)
        .when()
        .contentType(ContentType.URLENC)
        .formParam("token", token)
        .post(introspectionEndpoint);
  }

  public Response tryGetHealth(String path) {
    return given().when().get(base.resolve("health/" + path));
  }

  public Response tryGetRealm(String name) {
    return given().when().get(base.resolve("realms/" + name));
  }

  public Response tryGetAccountConsoleForRealm(String realm) {
    return given().when().get(base.resolve("realms/" + realm + "/account"));
  }

  public Response tryGetMasterAdminConsole() {
    return given().when().get(base.resolve("admin/master/console/"));
  }

  public Response tryGetAdminRoot() {
    return given().when().get(base.resolve("admin/"));
  }
}
