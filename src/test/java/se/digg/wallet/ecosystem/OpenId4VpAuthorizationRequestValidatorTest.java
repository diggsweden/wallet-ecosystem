// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OpenId4VpAuthorizationRequestValidatorTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static SignedJWT registrationCertificate;
  private static JWSHeader accessCertificateHeader;
  private static JWSSigner accessCertificateSigner;
  private final OpenId4VpAuthorizationRequestValidator validator =
      new OpenId4VpAuthorizationRequestValidator();

  @BeforeAll
  static void loadCertificates() throws Exception {
    registrationCertificate =
        CertificateTestSupport.loadRegistrationCertificateJwt();
    accessCertificateHeader = CertificateTestSupport.loadAccessCertificateHeader();
    accessCertificateSigner = CertificateTestSupport.loadAccessCertificateSigner();
  }

  @Test
  void acceptsAuthorizationRequestWithRegisteredClaims() throws Exception {
    var authorizationRequest = validUnsignedRequest();
    authorizationRequest.sign(accessCertificateSigner);

    validator.validateRelyingPartyAuthorizationRequest(authorizationRequest);
  }

  @Test
  void rejectsAuthorizationRequestSignedByUnexpectedKey() throws Exception {
    var authorizationRequest = validUnsignedRequest();
    authorizationRequest.sign(new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate()));

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));
    assertThat(exception.getMessage(),
        containsString("OpenId4VP Authorization Request not signed by access certificate key"));
  }

  @Test
  void rejectsAuthorizationRequestWithUnregisteredClaim() throws Exception {
    var claims = """
        [
          { "path": ["not_registered_claim"] }
        ]""";
    var requestWithUnregisteredClaim = new SignedJWT(
        accessCertificateHeader,
        authorizationRequest(registrationCertificate, claims));
    requestWithUnregisteredClaim.sign(accessCertificateSigner);

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(requestWithUnregisteredClaim));

    assertThat(exception.getMessage(), containsString("not_registered_claim"));
  }

  @Test
  void rejectsRegisteredClaimForUnregisteredCredentialType() throws Exception {
    var authorizationRequest = new SignedJWT(accessCertificateHeader,
        authorizationRequest(registrationCertificate,
            "[{\"path\":[\"family_name\"]}]", "urn:example:unregistered"));
    authorizationRequest.sign(accessCertificateSigner);

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));

    assertThat(exception.getMessage(), containsString("family_name"));
  }

  @Test
  void rejectsMixedRegisteredAndUnregisteredCredentialTypes() throws Exception {
    var authorizationRequest = new SignedJWT(accessCertificateHeader,
        authorizationRequest(registrationCertificate,
            "[{\"path\":[\"family_name\"]}]", "urn:eudi:pid:1", "urn:example:unregistered"));
    authorizationRequest.sign(accessCertificateSigner);

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));

    assertThat(exception.getMessage(), containsString("family_name"));
  }

  @Test
  void rejectsUnsupportedCredentialFormat() throws Exception {
    var authorizationRequest = new SignedJWT(accessCertificateHeader,
        JWTClaimsSet.parse("""
            {
              "verifier_info": [{"format": "registration_cert", "data": "%s"}],
              "dcql_query": {
                "credentials": [{"format": "mso_mdoc"}]
              }
            }
            """.formatted(registrationCertificate.serialize())));
    authorizationRequest.sign(accessCertificateSigner);

    var exception = assertThrows(IllegalArgumentException.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));

    assertThat(exception.getMessage(), containsString("Unsupported credential format 'mso_mdoc'"));
  }

  @Test
  void rejectsRegistrationCertificateForDifferentOrganization() throws Exception {
    var registrationCertificateWithDifferentOrganization = new SignedJWT(
        registrationCertificate.getHeader(),
        new JWTClaimsSet.Builder()
            .subject("different-organization")
            .build());
    CertificateTestSupport.signRegistrationCertificateJwt(
        registrationCertificateWithDifferentOrganization);
    var authorizationRequest = new SignedJWT(
        accessCertificateHeader,
        authorizationRequest(registrationCertificateWithDifferentOrganization, "[]"));
    authorizationRequest.sign(accessCertificateSigner);

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));

    assertThat(exception.getMessage(), containsString("different-organization"));
  }

  @Test
  void rejectsAuthorizationRequestWithoutRegistrationCertificate() throws Exception {
    var authorizationRequest = new SignedJWT(
        accessCertificateHeader,
        JWTClaimsSet.parse("""
            {
              "verifier_info": [
                {
                  "format": "some_other_format",
                  "data": "not-a-registration-certificate"
                }
              ]
            }
            """));

    var exception = assertThrows(IllegalArgumentException.class,
        () -> validator.getRegistrationCertificate(authorizationRequest));

    assertThat(exception.getMessage(), containsString("registration_cert"));
    assertThat(exception.getMessage(), containsString("some_other_format"));
  }

  private SignedJWT validUnsignedRequest() throws ParseException {
    var claims = """
        [
          { "path": ["family_name"] },
          { "path": ["given_name"] },
          { "path": ["personal_administrative_number"] },
          { "path": ["address", "street_address"] }
        ]""";
    var authorizationRequest = new SignedJWT(
        accessCertificateHeader,
        authorizationRequest(registrationCertificate, claims));
    return authorizationRequest;
  }

  private JWTClaimsSet authorizationRequest(SignedJWT registrationCertificate, String claimsArray)
      throws ParseException {
    return authorizationRequest(registrationCertificate, claimsArray, "urn:eudi:pid:1");
  }

  private JWTClaimsSet authorizationRequest(SignedJWT registrationCertificate, String claimsArray,
      String... vcts) throws ParseException {
    return JWTClaimsSet.parse("""
        {
          "verifier_info": [
            {
              "format": "registration_cert",
              "data": "%s"
            }
          ],
          "dcql_query": {
            "credentials": [
              {
                "format": "dc+sd-jwt",
                "meta": { "vct_values": %s },
                "claims": %s
              }
            ]
          }
        }
        """
        .formatted(registrationCertificate.serialize(), JSON.writeValueAsString(vcts),
            claimsArray));
  }
}
