// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AccessCertificateTest {
  private static final Path VERIFIER_KEYSTORE = Path.of(
      "config/certificates/verifier-access-certificate/verifier-access-certificate.p12");
  private static final String VERIFIER_ALIAS = "verifier_access_certificate";
  private static final String RP_CONTACT_URI = "https://localhost/demo-verifier";
  private static final String ACCESS_CERTIFICATE_POLICY_OID = "0.4.0.194118.1.2";
  private static final String ACCESS_CERTIFICATE_CPS_URI =
      "http://trust-source/verifier-access-certificate/cps.md";

  // Check that the RP contact URI is in the SAN.
  @Test
  void verifierAccessCertificateContainsRpContactInformationInSan() throws Exception {
    X509Certificate certificate = verifierCertificate();
    Collection<List<?>> subjectAlternativeNames = certificate.getSubjectAlternativeNames();

    assertTrue(hasUri(subjectAlternativeNames, RP_CONTACT_URI),
        "Verifier access certificate must contain the RP contact URI in its SAN");
  }

  // Check that TLS usage is not included.
  @Test
  void verifierAccessCertificateDoesNotContainTlsExtendedKeyUsage() throws Exception {
    X509Certificate certificate = verifierCertificate();

    assertNull(certificate.getExtendedKeyUsage(),
        "Verifier access certificate must not contain TLS extended key usage");
  }

  // Check that QC statements are not included.
  @Test
  void verifierAccessCertificateDoesNotContainQcStatements() throws Exception {
    X509Certificate certificate = verifierCertificate();

    assertNull(certificate.getExtensionValue("1.3.6.1.5.5.7.1.3"),
        "Verifier access certificate must not contain QC statements");
  }

  // Check that only the required key usages are included.
  @Test
  void verifierAccessCertificateUsesOnlySignatureAndNonRepudiationKeyUsage() throws Exception {
    X509Certificate certificate = verifierCertificate();
    boolean[] keyUsage = certificate.getKeyUsage();

    assertTrue(keyUsage[0], "digitalSignature must be enabled");
    assertTrue(keyUsage[1], "nonRepudiation must be enabled");
    for (int index = 2; index < keyUsage.length; index++) {
      assertFalse(keyUsage[index], "Unexpected key usage at index " + index);
    }
  }

  // Check that the required policy OID is present.
  @Test
  void verifierAccessCertificateContainsRequiredPolicyIdentifier() throws Exception {
    String certificateDetails = opensslCertificateDetails(verifierCertificate());

    assertTrue(certificateDetails.contains("Policy: " + ACCESS_CERTIFICATE_POLICY_OID),
        "Verifier access certificate must contain the required policy OID");
  }

  // Check that the CPS URI is present.
  @Test
  void verifierAccessCertificateContainsCpsUri() throws Exception {
    String certificateDetails = opensslCertificateDetails(verifierCertificate());

    assertTrue(certificateDetails.contains("CPS: " + ACCESS_CERTIFICATE_CPS_URI),
        "Verifier access certificate must contain the CPS URI");
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

  private X509Certificate verifierCertificate() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(VERIFIER_KEYSTORE)) {
      keyStore.load(input, verifierKeystorePassword());
    }
    return (X509Certificate) keyStore.getCertificate(VERIFIER_ALIAS);
  }

  private char[] verifierKeystorePassword() throws Exception {
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

  // SAN type 6 is a URI.
  private boolean hasUri(Collection<List<?>> subjectAlternativeNames, String expectedUri) {
    return subjectAlternativeNames != null
        && subjectAlternativeNames.stream().anyMatch(name -> name.size() == 2
            && Integer.valueOf(6).equals(name.get(0))
            && expectedUri.equals(name.get(1)));
  }
}
