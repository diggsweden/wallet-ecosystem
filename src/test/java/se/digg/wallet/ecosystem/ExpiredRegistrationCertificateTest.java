// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

import com.nimbusds.jwt.SignedJWT;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpiredRegistrationCertificateTest {
  private static final Path DIRECTORY = Path.of(
      "config/certificates/verifier-registration-certificate");

  @Test
  void expiredRegistrationRetainsTheTrustedSignatureAndRegisteredAttributes() throws Exception {
    var normal = CertificateTestSupport.loadRegistrationCertificateJwt();
    var expired = SignedJWT.parse(Files.readString(
        DIRECTORY.resolve("expired_registration_certificate.jwt")));
    var lote = SignedJWT.parse(Files.readString(
        Path.of("config/trust-source/signed/trusted-entities.json")));

    new ListOfTrustedEntities(lote).validateRegistrationCertificate(expired);

    assertThat(expired.getHeader().toJSONObject(), equalTo(normal.getHeader().toJSONObject()));
    var normalClaims = new HashMap<>(normal.getJWTClaimsSet().getClaims());
    var expiredClaims = new HashMap<>(expired.getJWTClaimsSet().getClaims());
    for (var claim : List.of("iat", "exp", "intended_use_id")) {
      normalClaims.remove(claim);
      expiredClaims.remove(claim);
    }
    assertThat(expiredClaims, equalTo(normalClaims));
    assertThat(expired.getJWTClaimsSet().getStringClaim("intended_use_id"),
        equalTo("expired-registration"));
    assertThat(expired.getJWTClaimsSet().getExpirationTime(), lessThan(new Date()));
    assertThat(expired.getJWTClaimsSet().getIssueTime(),
        lessThan(expired.getJWTClaimsSet().getExpirationTime()));
  }

  @Test
  void expiredRegistrationIsConfiguredOnlyForTheNegativeIntendedUse() throws Exception {
    var jwt = Files.readString(DIRECTORY.resolve("expired_registration_certificate.jwt")).trim();

    var environment = Files.readString(
        DIRECTORY.resolve("expired_registration_certificate.env")).trim();

    assertThat(environment, equalTo("VERIFIER_INTENDEDUSES_1_REGISTRATIONCERTIFICATE=" + jwt));
  }
}
