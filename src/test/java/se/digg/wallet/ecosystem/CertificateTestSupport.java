// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.util.ArrayList;

final class CertificateTestSupport {
  private static final char[] VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD =
      Property.VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD.getValue().toCharArray();
  private static final char[] VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD =
      Property.VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD.getValue().toCharArray();
  private static final Path ACCESS_CERTIFICATE_KEYSTORE = Path.of(
      "config/certificates/verifier-access-certificate/verifier-access-certificate.p12");
  private static final String ACCESS_CERTIFICATE_ALIAS = "verifier_access_certificate";
  private static final Path REGISTRATION_CERTIFICATE = Path.of(
      "config/certificates/verifier-registration-certificate/registration_certificate.jwt");
  private static final Path REGISTRATION_KEYSTORE = Path.of(
      "config/certificates/verifier-registration-certificate/"
          + "verifier-registration-certificate.p12");
  private static final String REGISTRATION_ALIAS = "registration";

  private CertificateTestSupport() {}

  static JWSHeader loadAccessCertificateHeader() throws Exception {
    var keyStore = loadKeyStore(
        ACCESS_CERTIFICATE_KEYSTORE, VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD);
    var certificateChain = new ArrayList<Base64>();
    for (var certificate : keyStore.getCertificateChain(ACCESS_CERTIFICATE_ALIAS)) {
      certificateChain.add(Base64.encode(certificate.getEncoded()));
    }
    return new JWSHeader.Builder(JWSAlgorithm.ES256)
        .x509CertChain(certificateChain)
        .build();
  }

  static X509Certificate loadAccessCertificateX509() throws Exception {
    var keyStore = loadKeyStore(
        ACCESS_CERTIFICATE_KEYSTORE, VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD);
    return (X509Certificate) keyStore.getCertificate(ACCESS_CERTIFICATE_ALIAS);
  }

  static SignedJWT loadRegistrationCertificateJwt() throws Exception {
    return SignedJWT.parse(Files.readString(REGISTRATION_CERTIFICATE));
  }

  static X509Certificate loadRegistrationCertificateX509() throws Exception {
    var keyStore = loadRegistrationKeyStore();
    return (X509Certificate) keyStore.getCertificate(REGISTRATION_ALIAS);
  }

  static void signRegistrationCertificateJwt(SignedJWT certificate) throws Exception {
    var privateKey = (ECPrivateKey) loadRegistrationKeyStore().getKey(
        REGISTRATION_ALIAS,
        VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD);
    certificate.sign(new ECDSASigner(privateKey));
  }

  private static KeyStore loadRegistrationKeyStore() throws Exception {
    return loadKeyStore(
        REGISTRATION_KEYSTORE,
        VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD);
  }

  private static KeyStore loadKeyStore(Path path, char[] password) throws Exception {
    var keyStore = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(path)) {
      keyStore.load(input, password);
    }
    return keyStore;
  }

}
