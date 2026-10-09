// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

public class ListOfTrustedEntities {

  private static final ObjectMapper LOTE_CLAIMS_OBJECT_MAPPER = JsonMapper.builder()
      .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build();
  private static final String WRPRC_ISSUANCE_SERVICE =
      "http://uri.etsi.org/19602/SvcType/WRPRC/Issuance";
  private static final String WRPAC_ISSUANCE_SERVICE =
      "http://uri.etsi.org/19602/SvcType/WRPAC/Issuance";

  private final SignedJWT lote;

  public ListOfTrustedEntities(SignedJWT lote) {
    this.lote = lote;
  }

  /**
   * Validates the signature of this list of trusted entities against the authority, then validates
   * the access certificate and the registration certificate against this list.
   */
  public void validateWalletRelyingPartyTrust(
      X509Certificate authority,
      X509Certificate accessCertificate,
      SignedJWT registrationCertificate) throws Exception {
    validateSignature(authority);
    validateAccessCertificate(accessCertificate);
    validateRegistrationCertificate(registrationCertificate);
  }

  void validateSignature(X509Certificate certificate) throws Exception {
    var loteSigner = certificateFromBase64(lote.getHeader().getX509CertChain().getFirst());

    validateCertificateChainAgainstTrust(List.of(loteSigner), Set.of(certificate));

    var verifier = new DefaultJWSVerifierFactory().createJWSVerifier(
        lote.getHeader(), loteSigner.getPublicKey());
    assertTrue(lote.verify(verifier), "LoTE was not signed by expected key");
  }

  void validateRegistrationCertificate(SignedJWT registrationCertificate)
      throws Exception {
    var certificateChain = registrationCertificate.getHeader().getX509CertChain().stream()
        .map(ListOfTrustedEntities::certificateFromBase64)
        .toList();

    validateRegistrationCertificateSignature(
        registrationCertificate, certificateChain.getFirst());

    var trustedCertificates = trustedCertificatesForService(WRPRC_ISSUANCE_SERVICE);
    var certificateChainToValidate = certificateChain.stream()
        // Do not include LoTE CAs in the certificate chain
        .filter(certificate -> !trustedCertificates.contains(certificate))
        .toList();
    validateCertificateChainAgainstTrust(certificateChainToValidate, trustedCertificates);
  }

  void validateAccessCertificate(X509Certificate accessCertificate) throws Exception {
    var trustedCertificates = trustedCertificatesForService(WRPAC_ISSUANCE_SERVICE);
    validateCertificateChainAgainstTrust(List.of(accessCertificate), trustedCertificates);
  }

  private static void validateRegistrationCertificateSignature(
      SignedJWT registrationCertificate, X509Certificate leafCertificate) throws JOSEException {
    var verifier = new DefaultJWSVerifierFactory().createJWSVerifier(
        registrationCertificate.getHeader(), leafCertificate.getPublicKey());
    assertTrue(registrationCertificate.verify(verifier),
        "The registration certificate was not signed by expected key");
  }

  private Set<X509Certificate> trustedCertificatesForService(String serviceType) {
    var claims = LOTE_CLAIMS_OBJECT_MAPPER.readValue(
        lote.getPayload().toString(), LoteClaims.class);
    var trustedCertificates = claims.lote().trustedEntitiesList().stream()
        .flatMap(entity -> entity.trustedEntityServices().stream())
        .filter(service -> serviceType.equals(
            service.serviceInformation().serviceTypeIdentifier()))
        .flatMap(service -> service.serviceInformation().serviceDigitalIdentity()
            .x509Certificates().stream())
        .map(certificate -> certificateFromBase64(certificate.val()))
        // Trust the issuing CA, not the leaf certificate
        .filter(certificate -> certificate.getBasicConstraints() >= 0)
        .collect(Collectors.toSet());

    assertThat(trustedCertificates, not(empty()));

    return trustedCertificates;
  }

  private static void validateCertificateChainAgainstTrust(
      List<X509Certificate> certificateChain, Set<X509Certificate> trustedCertificates)
      throws GeneralSecurityException {
    var certPath = CertificateFactory.getInstance("X.509")
        .generateCertPath(certificateChain);
    var nameConstraints = (byte[]) null; // no name constraints extension
    var trustAnchors = trustedCertificates.stream()
        .map(certificate -> new TrustAnchor(certificate, nameConstraints))
        .collect(Collectors.toSet());
    var parameters = new PKIXParameters(trustAnchors);
    parameters.setRevocationEnabled(false);
    CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
  }

  private static X509Certificate certificateFromBase64(Base64 certificate) {
    return certificateFromBase64(certificate.toString());
  }

  private static X509Certificate certificateFromBase64(String certificate) {
    try {
      return (X509Certificate) CertificateFactory.getInstance("X.509")
          .generateCertificate(new ByteArrayInputStream(java.util.Base64.getDecoder()
              .decode(certificate)));
    } catch (CertificateException e) {
      throw new IllegalArgumentException("Invalid Base64 X.509 certificate", e);
    }
  }

  private record LoteClaims(
      @JsonProperty("LoTE") Lote lote) {
    private record Lote(List<TrustedEntity> trustedEntitiesList) {
      private record TrustedEntity(List<TrustedService> trustedEntityServices) {
        private record TrustedService(ServiceInformation serviceInformation) {
          private record ServiceInformation(String serviceTypeIdentifier,
              ServiceDigitalIdentity serviceDigitalIdentity) {
            private record ServiceDigitalIdentity(List<X509CertificateEntry> x509Certificates) {
              private record X509CertificateEntry(
                  @JsonProperty("val") String val) {
              }
            }
          }
        }
      }
    }
  }
}
