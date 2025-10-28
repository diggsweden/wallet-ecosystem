// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.notNullValue;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class EndToEndTest {

  private static final String ISSUER_TOKEN_URL =
      "https://keycloak.wallet.local/idp/realms/pid-issuer-realm/protocol/openid-connect/token";
  private static final String WALLET_PROVIDER_WUA_URL =
      "https://wallet-provider.wallet.local/wallet-unit-attestation";
  private static final String PID_ISSUER_CREDENTIAL_URL =
      "https://pid-issuer.wallet.local/credential";
  private static final String PID_ISSUER_AUDIENCE = "https://issuer.dev.eudiw.se/pid-issuer";

  @Test
  void getCredential() throws Exception {
    // 1. Get access token for user
    String accessToken = given()
        .when()
        .contentType(ContentType.URLENC)
        .formParam("grant_type", "password")
        .formParam("client_id", "wallet-dev")
        .formParam("username", "tneal")
        .formParam("password", "password")
        .formParam("scope", "eu.europa.ec.eudi.pid_vc_sd_jwt")
        .post(ISSUER_TOKEN_URL)
        .then()
        .assertThat().statusCode(200)
        .and().body("access_token", notNullValue())
        .extract().path("access_token");

    // 2. Create JWK for wallet
    ECKey jwk = new ECKeyGenerator(Curve.P_256).generate();

    // 3. Get WUA
    String wua = given()
        .when()
        .contentType(ContentType.JSON)
        .body(String.format("""
            { "walletId": "%s", "jwk": %s }""",
            UUID.randomUUID(),
            new ObjectMapper().writeValueAsString(jwk.toPublicJWK().toJSONString())))
        .post(WALLET_PROVIDER_WUA_URL)
        .then()
        .assertThat().statusCode(200)
        .extract().body().asString();

    // 4. Create proof
    String proof = createProof(jwk, wua);

    // 5. Get credential
    given()
        .auth().oauth2(accessToken)
        .when()
        .contentType(ContentType.JSON)
        .body(String.format("""
            {
              "format": "vc+sd-jwt",
              "proof": {
                "proof_type": "jwt",
                "jwt": "%s"
              },
              "credential_configuration_id": "eu.europa.ec.eudi.pid_vc_sd_jwt"
            }
            """, proof))
        .post(PID_ISSUER_CREDENTIAL_URL)
        .then()
        .assertThat().statusCode(200)
        .and().body("credential", notNullValue());
  }

  private String createProof(ECKey jwk, String wua) throws JOSEException {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(new JOSEObjectType("openid4vci-proof+jwt"))
        .jwk(jwk.toPublicJWK())
        .build();

    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(jwk.toPublicJWK().toString())
        .audience(PID_ISSUER_AUDIENCE)
        .claim("nonce", UUID.randomUUID().toString())
        .claim("wua", wua)
        .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(jwk));
    return jwt.serialize();
  }
}
