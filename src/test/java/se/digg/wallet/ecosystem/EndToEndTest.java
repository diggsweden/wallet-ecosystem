// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.response.Response;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    VerifierBackendTransactionByReferenceResponse transactionResponse =
        verifierBackend.createVerificationRequestByReference(nonce, issuerChain, dcqlId);
    String transactionId = transactionResponse.transaction_id();
    String requestUri = transactionResponse.request_uri();

    // 2. Get authorization request
    Response authRequestResponse = verifierBackend.getAuthorizationRequest(requestUri);
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

    String sdJwtVc = issuanceHelper.issuePidCredentialForTylerNeal(bindingKey);

    // 4. Create vp_token
    String vpToken =
        issuanceHelper.createVpToken(
            sdJwtVc, bindingKey, nonce, VerifierBackendClient.VERIFIER_AUDIENCE);

    // 5. Post wallet response
    String vpTokenJson = String.format("{ \"%s\": [ \"%s\" ] }", dcqlId, vpToken);
    Response postWalletResponse =
        verifierBackend.postWalletResponse(responseUri, state, vpTokenJson);
    assertThat(postWalletResponse.getStatusCode(), is(200));

    // 6. Verify the received Verifiable Presentation Token
    Response verificationStatusResponse = verifierBackend.getVerificationStatus(transactionId);
    assertThat(verificationStatusResponse.getStatusCode(), is(200));

    Map<String, List<String>> vpTokenMap = verificationStatusResponse.jsonPath().getMap("vp_token");
    String returnedVpToken = vpTokenMap.get(dcqlId).getFirst();

    String issuerSignedJwtString = returnedVpToken.split("~")[0];
    SignedJWT issuerSignedJwt = SignedJWT.parse(issuerSignedJwtString);
    assertThat(issuerSignedJwt.getJWTClaimsSet().getIssuer(), is("https://localhost/pid-issuer"));

    Map<String, String> disclosedClaims = new HashMap<>();
    String[] parts = returnedVpToken.split("~");
    Arrays.stream(parts, 1, parts.length)
        .filter(part -> !part.contains("."))
        .forEach(
            part -> {
              try {
                String decoded = new String(Base64.getUrlDecoder().decode(part));
                JsonNode jsonArray = objectMapper.readTree(decoded);
                if (jsonArray.isArray() && jsonArray.size() == 3) {
                  String claimName = jsonArray.get(1).asText();
                  String claimValue = jsonArray.get(2).asText();
                  disclosedClaims.put(claimName, claimValue);
                }
              } catch (Exception e) {
                fail("Failed to parse SD-JWT disclosure: " + e.getMessage());
              }
            });

    assertThat(disclosedClaims.get("given_name"), is("Tyler"));
    assertThat(disclosedClaims.get("family_name"), is("Neal"));

    // 7. Verify Events Response
    Response verificationEventsResponse = verifierBackend.getVerificationEvents(transactionId);
    assertThat(verificationEventsResponse.getStatusCode(), is(200));
    List<String> events = verificationEventsResponse.jsonPath().getList("events.event");
    assertThat(
        events,
        is(
            List.of(
                "Transaction initialized",
                "Request object retrieved",
                "Wallet response posted",
                "Verifier got wallet response")));
  }
}
