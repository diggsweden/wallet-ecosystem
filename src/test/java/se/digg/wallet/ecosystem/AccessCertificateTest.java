// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
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
    String certificateDetails = opensslCertificateDetails(verifierCertificate);
    assertThat(certificateDetails, containsString("Policy: " + ACCESS_CERTIFICATE_POLICY_OID));
  }

  // Check that the CPS URI is present.
  @Test
  void verifierAccessCertificateContainsCpsUri() throws Exception {
    String certificateDetails = opensslCertificateDetails(verifierCertificate);

    assertThat(certificateDetails, containsString("CPS: " + ACCESS_CERTIFICATE_CPS_URI));
  }

  private String opensslCertificateDetails(X509Certificate certificate) throws Exception {
    Path certificateFile = Files.createTempFile("verifier-access-certificate-", ".der");
    try {
      Files.write(certificateFile, certificate.getEncoded());
      Process process = new ProcessBuilder("openssl", "x509", "-inform", "DER",
          "-in", certificateFile.toString(), "-text", "-noout")
          .redirectErrorStream(true)
          .start();
      String details = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertEquals(0, process.waitFor(), "OpenSSL could not read the certificate");
      return details;
    } finally {
      Files.deleteIfExists(certificateFile);
    }
  }

  @BeforeAll
  static void loadVerifierCertificate() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(VERIFIER_KEYSTORE)) {
      keyStore.load(input, verifierKeystorePassword());
    }
    verifierCertificate = (X509Certificate) keyStore.getCertificate(VERIFIER_ALIAS);
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
