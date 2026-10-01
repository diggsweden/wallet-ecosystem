// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static java.util.stream.Collectors.toMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.List;
import java.util.function.Function;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1String;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

public final class OpenId4VpAuthorizationRequestValidator {
  private static final String REGISTRATION_CERTIFICATE_FORMAT = "registration_cert";
  private static final String SUBJECT_CLAIM = "sub";
  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  private static final String ORGANIZATION_IDENTIFIER_OID = "2.5.4.97";
  private static final ObjectMapper OPENID4VP_CLAIMS_OBJECT_MAPPER = JsonMapper.builder()
      .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build();

  public OpenId4VpAuthorizationRequestValidator() {}

  public void validateRelyingPartyAuthorizationRequest(SignedJWT authorizationRequest)
      throws ParseException {
    var accessCertificate = getAccessCertificate(authorizationRequest);
    var registrationCertificate = getRegistrationCertificate(authorizationRequest);

    assertRegistrationCertificateMatchesAccessCertificate(
        accessCertificate, registrationCertificate);
    assertRequestedClaimsAreRegistered(authorizationRequest, registrationCertificate);
  }

  public SignedJWT getRegistrationCertificate(SignedJWT request) throws ParseException {
    var claims = authorizationClaims(request);
    var verifierInfoByFormat = claims.verifierInfo().stream().collect(toMap(
        AuthorizationRequestClaims.VerifierInfo::format,
        Function.identity()));

    if (!verifierInfoByFormat.containsKey(REGISTRATION_CERTIFICATE_FORMAT)) {
      throw new IllegalArgumentException("""
          Registration certificate is missing from verifier_info.
          Expected format: '%s'.
          Actual formats: '%s'.
          """.formatted(
          REGISTRATION_CERTIFICATE_FORMAT,
          verifierInfoByFormat.keySet()));
    }

    return SignedJWT.parse(verifierInfoByFormat.get(REGISTRATION_CERTIFICATE_FORMAT).data());
  }

  private void assertRequestedClaimsAreRegistered(
      SignedJWT authorizationRequest, SignedJWT registrationCertificate) throws ParseException {
    var authorizationClaims = authorizationClaims(authorizationRequest);
    var registrationClaims = registrationClaims(registrationCertificate);

    // ARF RPRC_21: every attribute requested in the presentation must be
    // registered.
    for (var requestedCredential : authorizationClaims.dcqlQuery().credentials()) {
      if (requestedCredential.claims() == null) {
        continue;
      }

      var registeredClaims = registrationClaims.credentials().stream()
          .filter(registeredCredential -> matchesCredentialType(
              requestedCredential, registeredCredential))
          .filter(registeredCredential -> registeredCredential.claim() != null)
          .flatMap(registeredCredential -> registeredCredential.claim().stream())
          .toList();

      assertThat(requestedCredential.claims(), everyItem(in(registeredClaims)));
    }
  }

  private X509Certificate getAccessCertificate(SignedJWT request) {
    var certificateChain = request.getHeader().getX509CertChain();
    if (certificateChain == null || certificateChain.isEmpty()) {
      throw new IllegalArgumentException(
          "Access certificate is missing from the authorization request");
    }
    return certificateFromBase64(certificateChain.getFirst());
  }

  private void assertRegistrationCertificateMatchesAccessCertificate(
      X509Certificate accessCertificate, SignedJWT registrationCertificate) throws ParseException {
    var registrationIdentifier = registrationCertificate.getJWTClaimsSet()
        .getStringClaim(SUBJECT_CLAIM);
    var accessIdentifier = accessCertificateOrganizationIdentifier(accessCertificate);

    assertThat(accessIdentifier, is(registrationIdentifier));
  }

  private boolean matchesCredentialType(
      AuthorizationRequestClaims.DcqlQuery.CredentialQuery requestedCredential,
      RegistrationCertificateClaims.RegisteredCredential registeredCredential) {
    if (!requestedCredential.format().equals(registeredCredential.format())) {
      return false;
    }
    var registeredMetadata = registeredCredential.meta();
    if (requestedCredential.vct() != null) {
      return registeredMetadata != null
          && registeredMetadata.vctValues() != null
          && registeredMetadata.vctValues().stream()
              .anyMatch(requestedCredential.vct()::equals);
    }

    return true;
  }

  private String accessCertificateOrganizationIdentifier(X509Certificate accessCertificate) {
    try {
      var subject = new LdapName(
          accessCertificate.getSubjectX500Principal().getName("RFC2253"));

      var rdnsByType = subject.getRdns().stream().collect(toMap(
          Rdn::getType,
          rdn -> asString(rdn.getValue())));

      if (!rdnsByType.containsKey(ORGANIZATION_IDENTIFIER_OID)) {
        throw new IllegalArgumentException("""
            Access certificate is missing an organization identifier.
            Expected rdn with type: '%s'.
            Actual rdns: '%s'.
            """.formatted(
            ORGANIZATION_IDENTIFIER_OID,
            rdnsByType));
      }
      return rdnsByType.get(ORGANIZATION_IDENTIFIER_OID);
    } catch (InvalidNameException e) {
      throw new IllegalArgumentException("Access certificate subject is malformed", e);
    }
  }

  private String asString(Object value) {
    if (!(value instanceof byte[] bytes)) {
      return value.toString();
    }

    try {
      var asn1Value = ASN1Primitive.fromByteArray(bytes);
      if (asn1Value instanceof ASN1String stringValue) {
        return stringValue.getString();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Access certificate organizationIdentifier '%s' is malformed".formatted(value), e);
    }
    throw new IllegalArgumentException(
        "Access certificate organizationIdentifier '%s' is not a string".formatted(value));
  }

  private X509Certificate certificateFromBase64(Base64 certificate) {
    try {
      return (X509Certificate) CertificateFactory.getInstance("X.509")
          .generateCertificate(new ByteArrayInputStream(certificate.decode()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Access certificate is malformed", e);
    }
  }

  private AuthorizationRequestClaims authorizationClaims(SignedJWT jwt) {
    return OPENID4VP_CLAIMS_OBJECT_MAPPER.readValue(
        jwt.getPayload().toString(), AuthorizationRequestClaims.class);
  }

  private RegistrationCertificateClaims registrationClaims(SignedJWT jwt) {
    return OPENID4VP_CLAIMS_OBJECT_MAPPER.readValue(
        jwt.getPayload().toString(), RegistrationCertificateClaims.class);
  }

  private record AuthorizationRequestClaims(
      List<VerifierInfo> verifierInfo,
      DcqlQuery dcqlQuery) {
    private record VerifierInfo(String format, String data) {
    }

    private record DcqlQuery(List<CredentialQuery> credentials) {
      private record CredentialQuery(
          String format,
          String vct,
          List<Claim> claims) {
      }
    }
  }

  private record RegistrationCertificateClaims(List<RegisteredCredential> credentials) {
    private record RegisteredCredential(
        String format,
        Metadata meta,
        List<Claim> claim) {
      private record Metadata(List<String> vctValues) {
      }
    }
  }

  private record Claim(List<String> path) {
  }
}
