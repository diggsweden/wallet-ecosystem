// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenId4VpAuthorizationRequestValidatorTest {
  private static SignedJWT registrationCertificate;
  private static JWSHeader accessCertificateHeader;
  private final OpenId4VpAuthorizationRequestValidator validator =
      new OpenId4VpAuthorizationRequestValidator();

  @BeforeAll
  static void loadCertificates() throws Exception {
    registrationCertificate =
        CertificateTestSupport.loadRegistrationCertificateJwt();
    accessCertificateHeader = CertificateTestSupport.loadAccessCertificateHeader();
  }

  @Test
  void acceptsAuthorizationRequestWithRegisteredClaims() throws Exception {
    var authorizationRequest = new SignedJWT(
        accessCertificateHeader,
        JWTClaimsSet.parse("""
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
                    "vct": "urn:eudi:pid:1",
                    "claims": [
                      { "path": ["family_name"] },
                      { "path": ["given_name"] },
                      { "path": ["personal_administrative_number"] },
                      { "path": ["address", "street_address"] }
                    ]
                  }
                ]
              }
            }
            """.formatted(registrationCertificate.serialize())));

    validator.validateRelyingPartyAuthorizationRequest(authorizationRequest);
  }

  @Test
  void rejectsAuthorizationRequestWithUnregisteredClaim() throws Exception {
    var requestWithUnregisteredClaim = new SignedJWT(
        accessCertificateHeader,
        JWTClaimsSet.parse("""
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
                    "vct": "urn:eudi:pid:1",
                    "claims": [
                      { "path": ["not_registered_claim"] }
                    ]
                  }
                ]
              }
            }
            """.formatted(registrationCertificate.serialize())));

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(requestWithUnregisteredClaim));

    assertThat(exception.getMessage(), containsString("not_registered_claim"));
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
        JWTClaimsSet.parse("""
            {
              "verifier_info": [
                {
                  "format": "registration_cert",
                  "data": "%s"
                }
              ]
            }
            """.formatted(registrationCertificateWithDifferentOrganization.serialize())));

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

}
