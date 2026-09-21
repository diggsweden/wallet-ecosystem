// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("The wallet ecosystem")
public class EndToEndTest {

  private final VerifierBackendClient verifierBackend = new VerifierBackendClient();

  public static Stream<Arguments> issuers() {
    return Stream.of(
        Arguments.argumentSet("internal", new IssuanceAgent(new InternalWalletClient())),
        Arguments.argumentSet("public", new IssuanceAgent(new PublicWalletClient())));
  }

  @ParameterizedTest
  @MethodSource("issuers")
  void supportsIssuanceAndPresentationOfPid(IssuanceAgent issuer) throws Exception {
    // 1. Initialize transaction
    String nonce = UUID.randomUUID().toString();
    String dcqlId = UUID.randomUUID().toString();

    VerifierPresentationResponse transaction =
        verifierBackend.createPresentationRequestByReference(nonce, dcqlId);
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
    String uniqueKid = UUID.randomUUID().toString();
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .keyID(uniqueKid)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    String rawCredential = issuer.issuePidCredential(bindingKey, "tneal", "password");

    // 4. Create vp_token
    String vpToken =
        VerifiablePresentationToken.asString(rawCredential, bindingKey, nonce);

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

    SdJwtVc sdJwtVc = SdJwtVc.parse(returnedVpToken);
    assertThat(sdJwtVc.getIssuer(), is(ServiceIdentifier.PID_ISSUER.toString()));

    assertThat(sdJwtVc.disclosedClaims().get("given_name"), is("Tyler"));
    assertThat(sdJwtVc.disclosedClaims().get("family_name"), is("Neal"));
    assertThat(sdJwtVc.disclosedClaims().get("personal_administrative_number"), is("195504162776"));

    // 7. Verify Events Response
    Response presentationEvents = verifierBackend.getPresentationEvents(transactionId);
    assertThat(presentationEvents.getStatusCode(), is(200));
    List<String> events = presentationEvents.jsonPath().getList("events.event");
    assertThat(events, is(List.of(
        "Transaction initialized",
        "Request object retrieved",
        "Wallet response posted",
        "Verifier got wallet response")));

    // 8. Verify Database Persistence (skip if not available in environment)
    if (Boolean.parseBoolean(Property.PID_ISSUER_DB_TESTING_ENABLED.getValue())) {
      try (java.sql.Connection conn = java.sql.DriverManager.getConnection(
          ServiceIdentifier.PID_ISSUER_DB.toString(), "pid-issuer",
          "pass");
          java.sql.PreparedStatement stmt = conn.prepareStatement(
              "SELECT client_status_list_uri, key_storage_status_list_uri "
                  + "FROM issued_credential ORDER BY issued_at DESC LIMIT 1");
          java.sql.ResultSet rs = stmt.executeQuery()) {

        if (!rs.next()) {
          org.junit.jupiter.api.Assertions.fail("Expected at least one credential in the database");
        }
        org.junit.jupiter.api.Assertions.assertEquals("http://trust-source/signed/status-list.jwt",
            rs.getString("client_status_list_uri"));
        org.junit.jupiter.api.Assertions.assertEquals("http://trust-source/signed/status-list.jwt",
            rs.getString("key_storage_status_list_uri"));
      }
    }
  }
}
