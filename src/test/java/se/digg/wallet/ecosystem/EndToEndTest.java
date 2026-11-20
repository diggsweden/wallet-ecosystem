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
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class EndToEndTest {

  private final VerifierBackendClient verifierBackend = new VerifierBackendClient();
  private final TrustSourceClient trustSource = new TrustSourceClient();
  private final OpenId4VpAuthorizationRequestValidator authorizationRequestValidator =
      new OpenId4VpAuthorizationRequestValidator();

  public static Stream<Arguments> testCases() {
    // The first case is the most common combination. Each other case varies exactly one thing.
    return Stream.of(
        Arguments.argumentSet(
            "Public route - direct_post",
            IssuanceAgent.builder().wallet(new PublicWalletClient()).build(),
            ResponseMode.DIRECT_POST),
        Arguments.argumentSet(
            "Internal route - direct_post",
            IssuanceAgent.builder().wallet(new InternalWalletClient()).build(),
            ResponseMode.DIRECT_POST),
        Arguments.argumentSet(
            "Public route - direct_post.jwt",
            IssuanceAgent.builder().wallet(new PublicWalletClient()).build(),
            ResponseMode.DIRECT_POST_JWT),
        Arguments.argumentSet(
            "Legacy client - wallet-dev - direct_post",
            IssuanceAgent.builder()
                .wallet(new InternalWalletClient())
                .clientId(IssuanceAgent.LEGACY_CLIENT_ID)
                .with(AccessTokenStrategy.DPOP)
                .build(),
            ResponseMode.DIRECT_POST));
  }

  @ParameterizedTest
  @MethodSource("testCases")
  void supportsIssuanceAndPresentationOfPid(IssuanceAgent issuer, ResponseMode responseMode)
      throws Exception {
    runPidIssuanceAndPresentationFlow(issuer, responseMode);
  }

  private void runPidIssuanceAndPresentationFlow(IssuanceAgent issuer, ResponseMode responseMode)
      throws Exception {
    // 1. Initialize transaction
    String nonce = UUID.randomUUID().toString();
    String dcqlId = UUID.randomUUID().toString();

    VerifierPresentationResponse transaction =
        verifierBackend.createPresentationRequestByReference(
            IntendedUse.PID, nonce, dcqlId, responseMode);

    // 2. Get authorization request
    Response authRequestResponse = given().baseUri(transaction.request_uri())
        .when().get()
        .then().extract().response();
    SignedJWT signedAuthRequest = SignedJWT.parse(authRequestResponse.body().asString());
    validateAuthorizationRequest(signedAuthRequest);

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
    Response postWalletResponse =
        verifierBackend.submitPresentation(
            signedAuthRequest, responseMode, Map.of(dcqlId, List.of(vpToken)));

    assertThat(postWalletResponse.getStatusCode(), is(200));

    // 6. Verify the received Verifiable Presentation Token
    Response response = verifierBackend.getPresentationsStatus(transaction.transaction_id());
    assertThat(response.getStatusCode(), is(200));

    Map<String, List<String>> vpTokenMap = response.jsonPath().getMap("vp_token");
    String returnedVpToken = vpTokenMap.get(dcqlId).getFirst();

    SdJwtVc sdJwtVc = SdJwtVc.parse(returnedVpToken);
    assertThat(sdJwtVc.getIssuer(), is(ServiceIdentifier.PID_ISSUER.toString()));

    assertThat(sdJwtVc.disclosedClaims().get("given_name"), is("Tyler"));
    assertThat(sdJwtVc.disclosedClaims().get("family_name"), is("Neal"));
    assertThat(sdJwtVc.disclosedClaims().get("personal_administrative_number"), is("195504162776"));

    // 7. Verify Events Response
    Response presentationEvents =
        verifierBackend.getPresentationEvents(transaction.transaction_id());
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

  @ParameterizedTest
  @MethodSource("testCases")
  void supportsIssuanceAndPresentationOfLearningCredential(
      IssuanceAgent issuer, ResponseMode responseMode) throws Exception {
    runLearningCredentialIssuanceAndPresentationFlow(issuer, responseMode);
  }

  private void runLearningCredentialIssuanceAndPresentationFlow(
      IssuanceAgent issuer, ResponseMode responseMode) throws Exception {
    String vct = "urn:eu.europa.ec.eudi:learning:credential:1";
    var transaction = verifierBackend.createPresentationRequestByValue(
        IntendedUse.LEARNING_CREDENTIAL, vct, "achievement_title", responseMode);
    var authorizationRequest = SignedJWT.parse(transaction.request());
    var requestClaims = authorizationRequest.getJWTClaimsSet();
    var dcqlId = JsonPath.from(authorizationRequest.getPayload().toString())
        .getString("dcql_query.credentials[0].id");
    validateAuthorizationRequest(authorizationRequest);

    var bindingKey = new ECKeyGenerator(Curve.P_256)
        .keyID(UUID.randomUUID().toString())
        .algorithm(JWSAlgorithm.ES256)
        .keyUse(KeyUse.SIGNATURE)
        .generate();
    var rawCredential = issuer.issueLearningCredential(bindingKey, "fstrom", "password");
    var vpToken = VerifiablePresentationToken.asString(
        rawCredential, bindingKey, requestClaims.getStringClaim("nonce"));

    verifierBackend.submitPresentation(
        authorizationRequest, responseMode, Map.of(dcqlId, List.of(vpToken)))
        .then().statusCode(200);

    var response = verifierBackend.getPresentationsStatus(transaction.transaction_id());
    assertThat(response.getStatusCode(), is(200));
    Map<String, List<String>> vpTokenMap = response.jsonPath().getMap("vp_token");
    var credential = SdJwtVc.parse(vpTokenMap.get(dcqlId).getFirst());
    assertThat(credential.getIssuer(), is(SdJwtVc.parse(rawCredential).getIssuer()));
    var credentialClaims = credential.issuerJwt().getJWTClaimsSet();
    assertThat(credentialClaims.getStringClaim("vct"), is(vct));
    assertThat(credentialClaims.getStringClaim("achievement_title"),
        is("Foundations of Applied AI in Business"));
  }

  private void validateAuthorizationRequest(SignedJWT request) throws Exception {
    authorizationRequestValidator.validateRelyingPartyAuthorizationRequest(request);
    new ListOfTrustedEntities(trustSource.fetchLote()).validateWalletRelyingPartyTrust(
        authorizationRequestValidator.getAccessCertificate(request),
        authorizationRequestValidator.getRegistrationCertificate(request));
  }
}
