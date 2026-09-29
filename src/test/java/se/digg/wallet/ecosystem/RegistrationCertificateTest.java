// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegistrationCertificateTest {
  private static X509Certificate registrationX509Certificate;
  private static SignedJWT registrationJwt;

  @BeforeAll
  static void loadRegistrationCertificate() throws Exception {
    registrationX509Certificate =
        CertificateTestSupport.loadRegistrationCertificateX509();
    registrationJwt = CertificateTestSupport.loadRegistrationCertificateJwt();
  }

  @Test
  void registrationJwtMatchesCertificateAndChain() throws Exception {
    var certificateChain = registrationJwt.getHeader().getX509CertChain();

    assertThat(registrationJwt.getHeader().getType().toString(), equalTo("rc-wrp+jwt"));
    assertThat(registrationJwt.getHeader().getAlgorithm(), equalTo(JWSAlgorithm.ES256));
    assertThat(certificateChain, hasSize(2));
    var leafCertificate = certificateFromBase64(certificateChain.getFirst());
    var caCertificate = certificateFromBase64(certificateChain.get(1));

    leafCertificate.verify(caCertificate.getPublicKey());
    registrationX509Certificate.verify(caCertificate.getPublicKey());
    assertThat(leafCertificate.getEncoded(), equalTo(registrationX509Certificate.getEncoded()));
    assertTrue(registrationJwt.verify(new ECDSAVerifier(
        (ECPublicKey) registrationX509Certificate.getPublicKey())));
  }

  private static X509Certificate certificateFromBase64(Base64 certificate) throws Exception {
    return (X509Certificate) CertificateFactory.getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(certificate.decode()));
  }

}
