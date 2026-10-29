// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KeycloakAbcaTest {

  private final WalletProviderClient walletProvider = new WalletProviderClient();
  private final KeycloakClient keycloak = new KeycloakClient();

  private static final Map<String, String> TOKEN_PARAMETERS = Map.of(
      "grant_type", "password",
      "username", "tneal",
      "password", "password",
      "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt");

  private static final ECKey WALLET_KEY;

  static {
    try {
      WALLET_KEY = new ECKeyGenerator(Curve.P_256).keyID("0").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Failed to generate test wallet key", e);
    }
  }

  public static Stream<Arguments> wiasAndExpectedStatus() throws Exception {
    String validWiaString = new WalletProviderClient().getWalletInstanceAttestation(WALLET_KEY);
    SignedJWT validWia = SignedJWT.parse(validWiaString);

    ECKey untrustedKey = new ECKeyGenerator(Curve.P_256).keyID("untrusted").generate();
    SignedJWT resignedWia =
        new SignedJWT(validWia.getHeader(), validWia.getJWTClaimsSet());
    resignedWia.sign(new ECDSASigner(untrustedKey));

    return Stream.of(
        Arguments.argumentSet("trusted WIA from wallet provider", validWia, 200),
        Arguments.argumentSet("WIA re-signed with untrusted key", resignedWia, 401));
  }

  @ParameterizedTest
  @MethodSource("wiasAndExpectedStatus")
  void acceptsOnlyTrustedWias(SignedJWT wia, int expectedStatusCode) throws Exception {
    String challenge = keycloak.getChallenge("pid-issuer-realm");
    String pop =
        keycloak.createClientAttestationPop("pid-issuer-realm", "eudiw", WALLET_KEY, challenge);

    Response response = keycloak.tryGetDpopAccessTokenWithClientAttestation(
        "pid-issuer-realm", "eudiw", WALLET_KEY, wia.serialize(), pop, TOKEN_PARAMETERS);

    if (expectedStatusCode == 200) {
      response.then()
          .assertThat()
          .statusCode(200)
          .body("access_token", notNullValue());
    } else {
      response.then()
          .assertThat()
          .statusCode(expectedStatusCode)
          .body("error", is("invalid_client"));
    }
  }

  @Test
  void rejectsMismatchedProofOfPossessionKey() throws Exception {
    ECKey walletKey = new ECKeyGenerator(Curve.P_256).keyID("0").generate();
    ECKey differentKey = new ECKeyGenerator(Curve.P_256).keyID("different").generate();

    String wia = walletProvider.getWalletInstanceAttestation(walletKey);
    String challenge = keycloak.getChallenge("pid-issuer-realm");
    String pop =
        keycloak.createClientAttestationPop("pid-issuer-realm", "eudiw", differentKey, challenge);

    Response response = keycloak.tryGetDpopAccessTokenWithClientAttestation(
        "pid-issuer-realm", "eudiw", walletKey, wia, pop, TOKEN_PARAMETERS);

    response.then()
        .assertThat()
        .statusCode(401)
        .body("error", is("invalid_client"));
  }

  @Test
  void authenticatesWithWalletInstanceAttestationAndPop() throws Exception {
    ECKey walletKey = new ECKeyGenerator(Curve.P_256).keyID("0").generate();

    // 1. Get WIA from wallet-provider (sub: eudiw)
    String wia = walletProvider.getWalletInstanceAttestation(walletKey);
    assertNotNull(wia);

    // 2. Get challenge from Keycloak
    URI challengeEndpoint = KEYCLOAK.getResourceRoot().resolve("realms/pid-issuer-realm/challenge");
    String challenge = given()
        .when()
        .post(challengeEndpoint)
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .path("attestation_challenge");
    assertNotNull(challenge);

    // 3. Create Client Attestation PoP JWT
    String realmIssuer = KEYCLOAK.getResourceRoot().resolve("realms/pid-issuer-realm").toString();
    JWSHeader popHeader = new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("oauth-client-attestation-pop+jwt"))
        .build();

    JWTClaimsSet popClaims = new JWTClaimsSet.Builder()
        .issuer("eudiw")
        .audience(List.of(realmIssuer))
        .claim("challenge", challenge)
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date.from(Instant.now()))
        .build();

    SignedJWT popJwt = new SignedJWT(popHeader, popClaims);
    popJwt.sign(new ECDSASigner(walletKey));
    String pop = popJwt.serialize();

    // 4. Create DPoP proof
    URI tokenEndpoint =
        KEYCLOAK.getResourceRoot().resolve("realms/pid-issuer-realm/protocol/openid-connect/token");
    String dpopProof = DpopUtil.createDpopProof(walletKey, tokenEndpoint.toString(), "POST");

    // 5. Request token using ABCA
    Response tokenResponse = given()
        .contentType(ContentType.URLENC)
        .header("DPoP", dpopProof)
        .header("OAuth-Client-Attestation", wia)
        .header("OAuth-Client-Attestation-PoP", pop)
        .formParams(Map.of(
            "grant_type", "password",
            "client_id", "eudiw",
            "username", "tneal",
            "password", "password",
            "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt"))
        .when()
        .post(tokenEndpoint)
        .then()
        .extract()
        .response();

    assertThat("Token request response: " + tokenResponse.body().asString(),
        tokenResponse.statusCode(), is(200));

    String accessToken = tokenResponse.jsonPath().getString("access_token");
    assertNotNull(accessToken);

    // 6. Verify client_status in token
    SignedJWT parsedAccessToken = SignedJWT.parse(accessToken);
    assertNotNull(parsedAccessToken.getJWTClaimsSet().getClaim("client_status"),
        "Access token must contain client_status claim mapped by client-status-protocol-mapper");

    // 7. Introspect token
    keycloak.introspectToken("pid-issuer-realm", accessToken, "pid-issuer-srv",
        Property.KEYCLOAK_PID_ISSUER_CLIENT_SECRET.getValue())
        .then()
        .assertThat()
        .statusCode(200)
        .body("active", is(true))
        .body("client_status", notNullValue());

    // 8. Issue PID credential via pid-issuer using ABCA token
    PidIssuerClient pidIssuer = new PidIssuerClient();
    String nonce = pidIssuer.getNonce(accessToken, walletKey);
    String keyAttestation = walletProvider.getKeyAttestation(walletKey, nonce);

    JWSHeader proofHeader = new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("openid4vci-proof+jwt"))
        .keyID("0")
        .customParam("key_attestation", keyAttestation)
        .build();

    JWTClaimsSet proofClaims = new JWTClaimsSet.Builder()
        .issuer("eudiw")
        .audience(ServiceIdentifier.PID_ISSUER.toString())
        .issueTime(Date.from(Instant.now()))
        .claim("nonce", nonce)
        .build();

    SignedJWT proofJwt = new SignedJWT(proofHeader, proofClaims);
    proofJwt.sign(new ECDSASigner(walletKey));
    String proof = proofJwt.serialize();

    ECKey encryptionKey = new ECKeyGenerator(Curve.P_256)
        .algorithm(JWEAlgorithm.ECDH_ES)
        .keyUse(KeyUse.ENCRYPTION)
        .generate();

    ECKey pidIssuerKey = pidIssuer.getCredentialRequestEncryptionKey();
    com.nimbusds.jose.Payload credentialResponse = pidIssuer.issueCredentials(
        accessToken, walletKey, encryptionKey, proof, pidIssuerKey);

    assertNotNull(credentialResponse);
    Map<String, Object> payloadJson = credentialResponse.toJSONObject();
    assertNotNull(payloadJson.get("credentials"));
  }
}
