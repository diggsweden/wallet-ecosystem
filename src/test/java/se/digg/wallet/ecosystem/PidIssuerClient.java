// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.factories.DefaultJWEDecrypterFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.EncryptedJWT;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import java.net.URI;
import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class PidIssuerClient {

  private final URI base;

  public PidIssuerClient() {
    base = ServiceIdentifier.PID_ISSUER.getResourceRoot();
  }

  public Response tryGetOpenIdCredentialIssuerMetadata(MetadataLocationStrategy strategy) {
    return given().when().get(strategy.applyTo(
        ServiceIdentifier.PID_ISSUER.toUri(),
        "/.well-known/openid-credential-issuer"));
  }

  public Response tryGetJwtVcIssuerMetadata(MetadataLocationStrategy strategy) {
    return given().get(strategy.applyTo(
        ServiceIdentifier.PID_ISSUER.toUri(),
        "/.well-known/jwt-vc-issuer"));
  }

  public Map<String, String> getUsefulLinks() {
    String responseBody = given().when().get(base.resolve("."))
        .then().assertThat().statusCode(200)
        .and().body("html.head.title",
            is("EU Digital Identity Wallet :: Generate new Credentials Offer"))
        .and().extract().body().asString();

    return Jsoup.parse(responseBody).selectStream(".table tbody tr")
        .map(row -> Stream.concat(
            row.selectStream("td").map(Element::text).map(String::trim),
            Stream.of("UNKNOWN", "UNKNOWN")).limit(2).toList())
        .collect(Collectors.toMap(List::getFirst, List::getLast));
  }

  public String getNonce(String accessToken, ECKey key) throws JOSEException {
    URI nonceEndpoint = this.base.resolve("wallet/nonceEndpoint");
    return given()
        .auth()
        .oauth2(accessToken)
        .header("DPoP",
            DpopUtil.createDpopProof(key, nonceEndpoint.toString(), "POST"))
        .when()
        .post(nonceEndpoint)
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .path("c_nonce");
  }

  public ECKey getCredentialRequestEncryptionKey() throws ParseException {
    Map<String, Object> jwksMap =
        getCredentialIssuerMetadata().extract().path("credential_request_encryption.jwks");

    JWKSet jwkSet = JWKSet.parse(jwksMap);

    return (ECKey) jwkSet.getKeys().getFirst();
  }

  List<URI> getAuthorizationServers() {
    return getCredentialIssuerMetadata().extract().<List<String>>path("authorization_servers")
        .stream().map(URI::create).collect(Collectors.toList());
  }

  private ValidatableResponse getCredentialIssuerMetadata() {
    return given()
        .when()
        .get(this.base.resolve(".well-known/openid-credential-issuer"))
        .then().statusCode(200);
  }

  public Payload issueCredentials(String accessToken, ECKey userJwk, ECKey jwk, String proof,
      String keyAttestation, ECKey pidIssuerCredentialRequestEncryptionKey)
      throws JOSEException, ParseException {
    return decryptPayload(
        postCredentials(
            accessToken, userJwk, encryptPayload(
                String.format("""
                    {
                      "format": "vc+sd-jwt",
                      "proofs": { "jwt": ["%s"] },
                      "key_attestation": "%s",
                      "credential_configuration_id": "eu.europa.ec.eudi.pid_vc_sd_jwt",
                      "credential_response_encryption": {
                        "jwk": %s,
                        "enc": "A128GCM",
                        "zip": "DEF"
                      }
                    }""",
                    proof, keyAttestation, jwk.toPublicJWK().toJSONString()),
                pidIssuerCredentialRequestEncryptionKey)),
        jwk.toECPrivateKey());
  }

  private String postCredentials(
      String accessToken, ECKey userJwk, String requestPayload) throws JOSEException {
    String credentialsEndpoint =
        this.base.resolve("wallet/credentialEndpoint").toString();
    String responsePayload =
        given()
            .auth()
            .oauth2(accessToken)
            .header("DPoP", DpopUtil.createDpopProof(userJwk,
                credentialsEndpoint, "POST"))
            .when()
            .contentType("application/jwt")
            .body(requestPayload)
            .post(credentialsEndpoint)
            .then()
            .assertThat()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    assertNotNull(responsePayload);

    return responsePayload;
  }

  private String encryptPayload(String payload, JWK publicKey) throws JOSEException {
    JWEObject jweObject =
        new JWEObject(new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A128GCM)
            .keyID(publicKey.getKeyID())
            .jwk(publicKey.toPublicJWK())
            .type(JOSEObjectType.JWT)
            .build(), new Payload(payload));

    jweObject.encrypt(new ECDHEncrypter(publicKey.toECKey()));

    return jweObject.serialize();
  }

  private Payload decryptPayload(String payload, ECPrivateKey privateKey)
      throws ParseException, JOSEException {

    EncryptedJWT encryptedJwt = EncryptedJWT.parse(payload);
    encryptedJwt.decrypt(
        new DefaultJWEDecrypterFactory()
            .createJWEDecrypter(encryptedJwt.getHeader(), privateKey));

    return encryptedJwt.getPayload();
  }
}
