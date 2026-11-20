// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class RegistrationCertificateTest {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static X509Certificate registrationX509Certificate;

  @BeforeAll
  static void loadRegistrationCertificate() throws Exception {
    registrationX509Certificate =
        CertificateTestSupport.loadRegistrationCertificateX509();
  }

  @ParameterizedTest
  @CsvSource({
      "1, registration_certificate.jwt, urn:eudi:pid:1, personal_administrative_number",
      "2, learning/registration_certificate.jwt,"
          + " urn:eu.europa.ec.eudi:learning:credential:1, achievement_title"
  })
  void configuredRegistrationsMatchSignedArtifacts(String intendedUseId,
      String jwtFile, String vct, String claim) throws Exception {
    var directory = Path.of("config/certificates/verifier-registration-certificate");
    var token = Files.readString(directory.resolve(jwtFile)).trim();
    var registrationJwt = SignedJWT.parse(token);
    final var certificateChain = registrationJwt.getHeader().getX509CertChain();

    assertThat(registrationJwt.getHeader().getType().toString(), equalTo("rc-wrp+jwt"));
    assertThat(registrationJwt.getHeader().getAlgorithm(), equalTo(JWSAlgorithm.ES256));
    assertThat(certificateChain, hasSize(2));
    var leafCertificate = certificateFromBase64(certificateChain.getFirst());
    var caCertificate = certificateFromBase64(certificateChain.get(1));

    leafCertificate.verify(caCertificate.getPublicKey());
    assertThat(leafCertificate.getEncoded(), equalTo(registrationX509Certificate.getEncoded()));
    assertThat(registrationJwt.verify(new ECDSAVerifier(
        (ECPublicKey) registrationX509Certificate.getPublicKey())), equalTo(true));
    assertRegistration(registrationJwt, intendedUseId, vct, claim);
    var credentials = registrationJwt.getJWTClaimsSet().getListClaim("credentials");
    assertThat(credentials, hasSize(1));
    var credential = (Map<?, ?>) credentials.getFirst();
    assertThat(((Map<?, ?>) credential.get("meta")).get("vct_values"), equalTo(List.of(vct)));
  }

  private static void assertRegistration(SignedJWT registration, String intendedUseId,
      String vct, String claim) {
    var claims = JSON.readTree(registration.getPayload().toString());
    assertThat(claims.path("intended_use_id").asString(), equalTo(intendedUseId));
    var expectedVct = JSON.valueToTree(vct);
    var registeredClaimPaths = claims.path("credentials").valueStream()
        .filter(credential -> "dc+sd-jwt".equals(credential.path("format").asString()))
        .filter(credential -> credential.at("/meta/vct_values").values().contains(expectedVct))
        .flatMap(credential -> credential.path("claim").valueStream())
        .map(registeredClaim -> registeredClaim.path("path"))
        .toList();
    var expectedClaimPath = JSON.valueToTree(List.of(claim.split("\\.")));
    assertThat("Claims registered for " + vct, registeredClaimPaths,
        hasItem(expectedClaimPath));
  }

  private static X509Certificate certificateFromBase64(Base64 certificate) throws Exception {
    return (X509Certificate) CertificateFactory.getInstance("X.509")
        .generateCertificate(new ByteArrayInputStream(certificate.decode()));
  }

}
