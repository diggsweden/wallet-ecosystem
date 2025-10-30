// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class EndToEndTest {

  public static final String PID_ISSUER_REALM =
      "https://keycloak.wallet.local/idp/realms/pid-issuer-realm";
  public static final String TOKEN_ENDPOINT = PID_ISSUER_REALM + "/protocol/openid-connect/token";
  public static final String PID_ISSUER_BASE = "https://pid-issuer.wallet.local";
  private static final String WALLET_PROVIDER_WUA_URL =
      "https://localhost/wallet-provider/wallet-unit-attestation";
  private static final String PID_ISSUER_CREDENTIAL_URL =
      PID_ISSUER_BASE + "/wallet/credentialEndpoint";
  private static final String PID_ISSUER_NONCE_URL = PID_ISSUER_BASE + "/wallet/nonceEndpoint";
  private static final String PID_ISSUER_METADATA_URL =
      PID_ISSUER_BASE + "/.well-known/openid-credential-issuer";

  @Test
  void getCredential() throws Exception {
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();
    String dpopProof = DpopUtil.createDpopProof(userJwk, TOKEN_ENDPOINT, "POST");

    // 1. Get access token for user
    String accessToken =
        given()
            .when()
            .contentType(ContentType.URLENC)
            .header("DPoP", dpopProof)
            .formParam("grant_type", "password")
            .formParam("client_id", "wallet-dev")
            .formParam("username", "tneal")
            .formParam("password", "password")
            .formParam("scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt")
            .formParam("role", "user")
            .post(TOKEN_ENDPOINT)
            .then()
            .assertThat()
            .statusCode(200)
            .and()
            .body("access_token", notNullValue())
            .body("token_type", org.hamcrest.CoreMatchers.equalTo("DPoP"))
            .extract()
            .path("access_token");

    // 2. Create JWK for wallet
    ECKey jwk =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();

    // 3. Get WUA
    String walletAttestation =
        given()
            .when()
            .contentType(ContentType.JSON)
            .body(
                String.format(
                    """
                        { "walletId": "%s", "jwk": %s }
                        """,
                    UUID.randomUUID(),
                    new ObjectMapper().writeValueAsString(jwk.toPublicJWK().toJSONString())))
            .post(WALLET_PROVIDER_WUA_URL)
            .then()
            .assertThat()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    // 4. Get nonce
    String nonce =
        given()
            .auth()
            .oauth2(accessToken)
            .header("DPoP", DpopUtil.createDpopProof(userJwk, PID_ISSUER_NONCE_URL, "POST"))
            .when()
            .post(PID_ISSUER_NONCE_URL)
            .then()
            .assertThat()
            .statusCode(200)
            .extract()
            .path("c_nonce");

    // 5. Create proof
    String proof = createProof(jwk, walletAttestation, nonce);
    String credentialRequestBody =
        String.format(
            """
                {
                  "format": "vc+sd-jwt",
                  "proofs": { "jwt": ["%s"] },
                  "credential_configuration_id": "eu.europa.ec.eudi.pid_vc_sd_jwt",
                  "credential_response_encryption": {
                    "jwk": %s,
                    "enc": "A128GCM",
                    "zip": "DEF"
                  }
                }""",
            proof, jwk.toPublicJWK().toJSONString());

    String encryptedPayload = encryptPayload(credentialRequestBody, getIssuerEncryptionKey());

    // 6. Get credential from issuer
    String pidJwt =
        given()
            .auth()
            .oauth2(accessToken)
            .header("DPoP", DpopUtil.createDpopProof(userJwk, PID_ISSUER_CREDENTIAL_URL, "POST"))
            .when()
            .contentType("application/jwt")
            .body(encryptedPayload)
            .post(PID_ISSUER_CREDENTIAL_URL)
            .then()
            .assertThat()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    assertNotNull(pidJwt);
    EncryptedJWT encryptedJwt = EncryptedJWT.parse(pidJwt);

    encryptedJwt.decrypt(
        new DefaultJWEDecrypterFactory()
            .createJWEDecrypter(encryptedJwt.getHeader(), jwk.toECPrivateKey()));

    Payload payload = encryptedJwt.getPayload();
    assertEquals(PID_ISSUER_BASE, payload.toJSONObject().get("iss"));

    var credentials = payload.toJSONObject().get("credentials");
    assertThat(credentials, instanceOf(List.class));
    assertThat((List<?>) credentials, not(empty()));
  }

  private ECKey getIssuerEncryptionKey() throws Exception {
    String issuerMetadata =
        given().when().get(PID_ISSUER_METADATA_URL).then().statusCode(200).extract().asString();

    JsonPath metadataPath = new JsonPath(issuerMetadata);
    Map<String, Object> jwksMap = metadataPath.getMap("credential_request_encryption.jwks");
    JWKSet jwkSet = JWKSet.parse(jwksMap);

    return (ECKey) jwkSet.getKeys().getFirst();
  }

  private String createProof(ECKey jwk, String wua, String nonce) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .jwk(jwk.toPublicJWK())
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(jwk.toPublicJWK().toString())
            .audience(PID_ISSUER_BASE)
            .issueTime(Date.from(Instant.now()))
            .claim("nonce", nonce)
            .claim("wua", wua)
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(jwk));
    return jwt.serialize();
  }

  private String encryptPayload(String payload, JWK publicKey) throws JOSEException {
    JWEHeader header =
        new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
            .keyID(publicKey.getKeyID())
            .jwk(publicKey.toPublicJWK())
            .type(JOSEObjectType.JWT)
            .build();

    JWEObject jweObject = new JWEObject(header, new Payload(payload));
    jweObject.encrypt(new ECDHEncrypter(publicKey.toECKey()));
    return jweObject.serialize();
  }
}
