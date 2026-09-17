// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AccessCertificateTest {
  private static final Path VERIFIER_KEYSTORE = Path.of(
      "config/certificates/verifier/verifier_backend.p12");
  private static final String VERIFIER_ALIAS = "verifier_backend";
  private static final String RP_CONTACT_URI = "https://localhost/demo-verifier";

  @Test
  void verifierAccessCertificateContainsRpContactInformationInSan() throws Exception {
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(VERIFIER_KEYSTORE)) {
      keyStore.load(input, verifierKeystorePassword());
    }

    X509Certificate certificate = (X509Certificate) keyStore.getCertificate(VERIFIER_ALIAS);
    Collection<List<?>> subjectAlternativeNames = certificate.getSubjectAlternativeNames();

    assertTrue(hasUri(subjectAlternativeNames, RP_CONTACT_URI),
        "Verifier access certificate must contain the RP contact URI in its SAN");
  }

  private char[] verifierKeystorePassword() throws Exception {
    String password = System.getenv("VERIFIER_KEYSTORE_PASSWORD");
    if (password == null) {
      Properties dotenv = new Properties();
      try (InputStream input = Files.newInputStream(Path.of(".env"))) {
        dotenv.load(input);
      }
      password = dotenv.getProperty("VERIFIER_KEYSTORE_PASSWORD", "verifier_password");
    }
    return password.toCharArray();
  }

  private boolean hasUri(Collection<List<?>> subjectAlternativeNames, String expectedUri) {
    return subjectAlternativeNames != null
        && subjectAlternativeNames.stream().anyMatch(name -> name.size() == 2
            && Integer.valueOf(6).equals(name.get(0))
            && expectedUri.equals(name.get(1)));
  }
}
