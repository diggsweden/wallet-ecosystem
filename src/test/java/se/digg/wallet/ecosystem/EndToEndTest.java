// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class EndToEndTest {

  private final VerifierBackendClient verifierBackend = new VerifierBackendClient();
  private final IssuanceHelper issuanceHelper = new IssuanceHelper();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void getCredential() throws Exception {
    // 1. Initialize transaction
    String issuerChain = issuanceHelper.getIssuerChain();
    String nonce = UUID.randomUUID().toString();
    String dcqlId = UUID.randomUUID().toString();

    VerifierPresentationResponse transaction =
        verifierBackend.createPresentationRequestByReference(nonce, issuerChain, dcqlId);
    String transactionId = transaction.transaction_id();
    String requestUri = transaction.request_uri();

    // 2. Get authorization request
    Response authRequestResponse =
        given().baseUri(requestUri).when().get().then().extract().response();
    String authRequest = authRequestResponse.body().asString();
    SignedJWT signedAuthRequest = SignedJWT.parse(authRequest);
    String state = signedAuthRequest.getJWTClaimsSet().getStringClaim("state");
    String responseUri = signedAuthRequest.getJWTClaimsSet().getStringClaim("response_uri");

    // 3. Get credential
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    String sdJwtVc = issuanceHelper.issuePidCredential(bindingKey, "tneal", "password");

    // 4. Create vp_token
    String vpToken =
        issuanceHelper.createVpToken(
            sdJwtVc, bindingKey, nonce, VerifierBackendClient.VERIFIER_AUDIENCE);

    // 5. Post wallet response
    String vpTokenJson = String.format("{ \"%s\": [ \"%s\" ] }", dcqlId, vpToken);
    Response postWalletResponse =
        given()
            .baseUri(responseUri)
            .contentType(ContentType.URLENC)
            .formParam("state", state)
            .formParam("vp_token", vpTokenJson)
            .when()
            .post()
            .then()
            .extract()
            .response();

    assertThat(postWalletResponse.getStatusCode(), is(200));

    // 6. Verify the received Verifiable Presentation Token
    Response response = verifierBackend.getPresentationsStatus(transactionId);
    assertThat(response.getStatusCode(), is(200));

    Map<String, List<String>> vpTokenMap = response.jsonPath().getMap("vp_token");
    String returnedVpToken = vpTokenMap.get(dcqlId).getFirst();

    String issuerSignedJwtString = returnedVpToken.split("~")[0];
    SignedJWT issuerSignedJwt = SignedJWT.parse(issuerSignedJwtString);
    assertThat(issuerSignedJwt.getJWTClaimsSet().getIssuer(), is("https://localhost/pid-issuer"));

    Map<String, String> disclosedClaims =
        Arrays.stream(returnedVpToken.split("~"))
            .skip(1)
            .filter(part -> !part.contains("."))
            .map(part -> new String(Base64.getUrlDecoder().decode(part)))
            .map(
                decoded -> {
                  try {
                    return objectMapper.readTree(decoded);
                  } catch (Exception e) {
                    throw new RuntimeException("Failed to parse SD-JWT disclosure", e);
                  }
                })
            .filter(node -> node.isArray() && node.size() == 3)
            .map(node -> List.of(node.get(1).asText(), node.get(2).asText()))
            .collect(Collectors.toMap(List::getFirst, List::getLast, (a, b) -> b));

    assertThat(disclosedClaims.get("given_name"), is("Tyler"));
    assertThat(disclosedClaims.get("family_name"), is("Neal"));

    // 7. Verify Events Response
    Response presentationEvents = verifierBackend.getPresentationEvents(transactionId);
    assertThat(presentationEvents.getStatusCode(), is(200));
    List<String> events = presentationEvents.jsonPath().getList("events.event");
    assertThat(events, is(List.of(
        "Transaction initialized",
        "Request object retrieved",
        "Wallet response posted",
        "Verifier got wallet response")));
  }
}
