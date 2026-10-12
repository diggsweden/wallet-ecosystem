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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OpenId4VpAuthorizationRequestValidatorTest {
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
    var authorizationRequest = new SignedJWT(accessCertificateHeader, validClaims().build());
    authorizationRequest.sign(accessCertificateSigner);

    validator.validateRelyingPartyAuthorizationRequest(authorizationRequest);
  }

  @Test
  void rejectsAuthorizationRequestSignedByUnexpectedKey() throws Exception {
    var authorizationRequest = new SignedJWT(accessCertificateHeader, validClaims().build());

    authorizationRequest.sign(new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate()));

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));
    assertThat(exception.getMessage(),
        containsString("OpenId4VP Authorization Request not signed by access certificate key"));
  }

  @Test
  void rejectsAuthorizationRequestWithUnregisteredClaim() throws Exception {
    var requestWithUnregisteredClaim = new SignedJWT(
        accessCertificateHeader,
        validClaims().claim("dcql_query", dcqlQueryClaim(
            List.of(dcqlCredentialsClaim("not_registered_claim")))).build());

    requestWithUnregisteredClaim.sign(accessCertificateSigner);

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
        validClaims().claim("verifier_info",
            verifierInfoClaim(registrationCertificateWithDifferentOrganization)).build());
    authorizationRequest.sign(accessCertificateSigner);

    var exception = assertThrows(AssertionError.class,
        () -> validator.validateRelyingPartyAuthorizationRequest(authorizationRequest));

    assertThat(exception.getMessage(), containsString("different-organization"));
  }

  @Test
  void rejectsAuthorizationRequestWithoutRegistrationCertificate() {
    var authorizationRequest = new SignedJWT(
        accessCertificateHeader, validClaims().claim("verifier_info",
            verifierInfoClaim("some_other_format", "not-a-registration-certificate")).build());

    var exception = assertThrows(IllegalArgumentException.class,
        () -> validator.getRegistrationCertificate(authorizationRequest));

    assertThat(exception.getMessage(), containsString("registration_cert"));
    assertThat(exception.getMessage(), containsString("some_other_format"));
  }

  private JWTClaimsSet.Builder validClaims() {
    return new JWTClaimsSet.Builder()
        .claim("verifier_info", verifierInfoClaim(registrationCertificate))
        .claim("dcql_query", dcqlQueryClaim(List.of(
            dcqlCredentialsClaim("family_name"),
            dcqlCredentialsClaim("given_name"),
            dcqlCredentialsClaim("personal_administrative_number"),
            dcqlCredentialsClaim("address", "street_address"))));
  }

  private List<Map<String, String>> verifierInfoClaim(
      SignedJWT registrationCertificate) {
    return verifierInfoClaim(
        "registration_cert",
        registrationCertificate.serialize());
  }

  private List<Map<String, String>> verifierInfoClaim(
      String format, String data) {
    return List.of(Map.of(
        "format", format,
        "data", data));
  }

  private Map<String, List<String>> dcqlCredentialsClaim(String... pathFragments) {
    return Map.of("path", Arrays.asList(pathFragments));
  }

  private Map<String, List<Map<String, Object>>> dcqlQueryClaim(
      List<Map<String, List<String>>> credentialsClaims) {
    return Map.of("credentials", List.of(Map.of(
        "format", "dc+sd-jwt",
        "vct", "urn:eudi:pid:1",
        "claims", credentialsClaims)));
  }
}
