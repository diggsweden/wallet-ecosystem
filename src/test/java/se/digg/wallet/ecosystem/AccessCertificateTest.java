// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.bouncycastle.asn1.ASN1IA5String;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.PolicyQualifierId;
import org.bouncycastle.asn1.x509.PolicyQualifierInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AccessCertificateTest {
  private static final Path VERIFIER_KEYSTORE = Path.of(
      "config/certificates/verifier-access-certificate/verifier-access-certificate.p12");
  private static final String VERIFIER_ALIAS = "verifier_access_certificate";
  private static final String RP_CONTACT_URI = "https://localhost/demo-verifier";
  private static final int URI_SAN_TYPE = 6;
  private static final String ACCESS_CERTIFICATE_POLICY_OID = "0.4.0.194118.1.2";
  private static final String QC_STATEMENTS_OID = "1.3.6.1.5.5.7.1.3";
  private static final String ACCESS_CERTIFICATE_CPS_URI =
      "http://trust-source/verifier-access-certificate/cps.md";
  private static X509Certificate verifierCertificate;
  private static CertificatePolicies verifierCertificatePolicies;

  // Check that the RP contact URI is in the SAN.
  @Test
  void verifierAccessCertificateContainsRpContactInformationInSan() throws Exception {
    Collection<List<?>> subjectAlternativeNames = verifierCertificate.getSubjectAlternativeNames();

    assertThat(subjectAlternativeNames, hasItem(List.of(URI_SAN_TYPE, RP_CONTACT_URI)));
  }

  // Check that TLS usage is not included.
  @Test
  void verifierAccessCertificateDoesNotContainTlsExtendedKeyUsage() throws Exception {
    assertNull(verifierCertificate.getExtendedKeyUsage(),
        "Verifier access certificate must not contain TLS extended key usage");
  }

  // Check that QC statements are not included.
  @Test
  void verifierAccessCertificateDoesNotContainQcStatements() throws Exception {
    assertThat(verifierCertificate.getCriticalExtensionOIDs(), not(hasItem(QC_STATEMENTS_OID)));
    assertThat(verifierCertificate.getNonCriticalExtensionOIDs(), not(hasItem(QC_STATEMENTS_OID)));
  }

  // Check that only the required key usages are included.
  @Test
  void verifierAccessCertificateUsesOnlySignatureAndNonRepudiationKeyUsage() throws Exception {
    boolean[] keyUsage = verifierCertificate.getKeyUsage();

    assertTrue(keyUsage[0], "digitalSignature must be enabled");
    assertTrue(keyUsage[1], "nonRepudiation must be enabled");
    for (int index = 2; index < keyUsage.length; index++) {
      assertFalse(keyUsage[index], "Unexpected key usage at index " + index);
    }
  }

  // Check that the required policy OID is present.
  @Test
  void verifierAccessCertificateContainsRequiredPolicyIdentifier() throws Exception {
    assertThat(certificatePolicyOids(), hasItem(ACCESS_CERTIFICATE_POLICY_OID));
  }

  // Check that the CPS URI is present.
  @Test
  void verifierAccessCertificateContainsCpsUri() throws Exception {
    assertThat(certificatePolicyCpsUris(), hasItem(ACCESS_CERTIFICATE_CPS_URI));
  }

  private Collection<String> certificatePolicyOids() {
    return Arrays.stream(verifierCertificatePolicies.getPolicyInformation())
        .map(PolicyInformation::getPolicyIdentifier)
        .map(ASN1ObjectIdentifier::getId)
        .toList();
  }

  private Collection<String> certificatePolicyCpsUris() {
    return Arrays.stream(verifierCertificatePolicies.getPolicyInformation())
        .filter(policy -> policy.getPolicyQualifiers() != null)
        .flatMap(policy -> Arrays.stream(policy.getPolicyQualifiers().toArray()))
        .map(PolicyQualifierInfo::getInstance)
        .filter(qualifier -> PolicyQualifierId.id_qt_cps.equals(qualifier.getPolicyQualifierId()))
        .map(PolicyQualifierInfo::getQualifier)
        .map(ASN1IA5String::getInstance)
        .map(ASN1IA5String::getString)
        .toList();
  }

  private static CertificatePolicies certificatePolicies(X509Certificate certificate)
      throws Exception {
    X509CertificateHolder certificateHolder = new X509CertificateHolder(certificate.getEncoded());
    Extension extension = certificateHolder.getExtension(Extension.certificatePolicies);
    if (extension == null) {
      return new CertificatePolicies(new PolicyInformation[0]);
    }
    return CertificatePolicies.getInstance(extension.getParsedValue());
  }

  @BeforeAll
  static void loadVerifierCertificate() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(VERIFIER_KEYSTORE)) {
      keyStore.load(input, verifierKeystorePassword());
    }
    verifierCertificate = (X509Certificate) keyStore.getCertificate(VERIFIER_ALIAS);
    verifierCertificatePolicies = certificatePolicies(verifierCertificate);
  }

  private static char[] verifierKeystorePassword() throws Exception {
    String password = System.getenv("VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD");
    if (password == null) {
      Properties dotenv = new Properties();
      try (InputStream input = Files.newInputStream(Path.of(".env"))) {
        dotenv.load(input);
      }
      password = dotenv.getProperty("VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD");
      if (password == null) {
        password = dotenv.getProperty("VERIFIER_KEYSTORE_PASSWORD", "verifier_password");
      }
    }
    return password.toCharArray();
  }

}
