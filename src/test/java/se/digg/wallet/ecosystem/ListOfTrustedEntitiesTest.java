// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXReason;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ListOfTrustedEntitiesTest {
  private static final Path LOTE_PATH = Path.of(
      "config/trust-source/signed/trusted-entities.json");
  private static final Path TRUST_SOURCE_CA_PATH = Path.of(
      "config/certificates/ca/trust-source/ca.pem");
  private static SignedJWT configuredLote;
  private static X509Certificate configuredAccessCertificate;
  private static SignedJWT configuredRegistrationCertificate;
  private static X509Certificate configuredTrustSourceCa;

  @BeforeAll
  static void beforeAll() throws Exception {
    configuredAccessCertificate = CertificateTestSupport.loadAccessCertificateX509();
    configuredRegistrationCertificate = CertificateTestSupport.loadRegistrationCertificateJwt();
    configuredLote = SignedJWT.parse(Files.readString(LOTE_PATH));
    try (var input = Files.newInputStream(TRUST_SOURCE_CA_PATH)) {
      configuredTrustSourceCa =
          (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
    }
  }

  private final ListOfTrustedEntities subject = new ListOfTrustedEntities(configuredLote);

  @Test
  void acceptsTrustedLoteAndRequest() throws Exception {
    subject.validateWalletRelyingPartyTrust(configuredTrustSourceCa,
        configuredAccessCertificate, configuredRegistrationCertificate);
  }

  @Test
  void rejectsWalletRelyingPartyTrustWithUntrustedAccessCertificate()
      throws Exception {
    var untrustedAccessCertificate =
        CertificateTestSupport.loadRegistrationCertificateX509();

    var exception = assertThrows(
        CertPathValidatorException.class,
        () -> subject.validateWalletRelyingPartyTrust(configuredTrustSourceCa,
            untrustedAccessCertificate, configuredRegistrationCertificate));

    assertThat(exception.getReason(), is(PKIXReason.NO_TRUST_ANCHOR));
  }

  @Test
  void acceptsConfiguredAccessCertificate() throws Exception {
    subject.validateAccessCertificate(configuredAccessCertificate);
  }

  @Test
  void rejectsAccessCertificateFromAnotherCa() throws Exception {
    var untrustedAccessCertificate = CertificateTestSupport.loadRegistrationCertificateX509();

    var exception = assertThrows(CertPathValidatorException.class,
        () -> subject.validateAccessCertificate(untrustedAccessCertificate));
    assertThat(exception.getReason(), is(PKIXReason.NO_TRUST_ANCHOR));
  }

  @Test
  void acceptsConfiguredRegistrationCertificate() throws Exception {
    subject.validateRegistrationCertificate(configuredRegistrationCertificate);
  }

  @Test
  void rejectsRegistrationCertificateWithInvalidSignature() throws Exception {
    var registrationCertificateSignedByAnotherKey = signWithAnotherKey(
        configuredRegistrationCertificate);

    var exception = assertThrows(AssertionError.class,
        () -> subject.validateRegistrationCertificate(
            registrationCertificateSignedByAnotherKey));
    assertThat(exception.getMessage(),
        containsString("The registration certificate was not signed by expected key"));
  }

  @Test
  void rejectsLoteWhenSignerIsNotTrustedByTrustSourceCa() throws Exception {
    var unrelatedCertificate = CertificateTestSupport.loadAccessCertificateX509();
    var exception = assertThrows(CertPathValidatorException.class,
        () -> subject.validateSignature(unrelatedCertificate));
    assertThat(exception.getReason(), is(PKIXReason.NO_TRUST_ANCHOR));
  }

  @Test
  void rejectsLoteWithInvalidSignature() throws Exception {
    var loteWithInvalidSignature = new ListOfTrustedEntities(signWithAnotherKey(configuredLote));
    var exception = assertThrows(AssertionError.class,
        () -> loteWithInvalidSignature.validateSignature(configuredTrustSourceCa));
    assertThat(exception.getMessage(), containsString("LoTE was not signed by expected key"));
  }

  private static SignedJWT signWithAnotherKey(SignedJWT toSign)
      throws JOSEException, ParseException {
    var signedJwt = new SignedJWT(toSign.getHeader(), toSign.getJWTClaimsSet());
    signedJwt.sign(new ECDSASigner(new ECKeyGenerator(Curve.P_256).generate()));
    return signedJwt;
  }

}
