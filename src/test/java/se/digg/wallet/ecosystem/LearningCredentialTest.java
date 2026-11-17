// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasEntry;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Issues a Learning Credential (attribute attestation) from the PID issuer. */
public class LearningCredentialTest {

  private static final String VCT = "urn:eu.europa.ec.eudi:learning:credential:1";

  /** The names come from the Keycloak user the credential is issued to. */
  @ParameterizedTest
  @CsvSource({
      "fstrom, Fredrik, Ström",
      "knyberg, Klaudia Anastasia, Nyberg"
  })
  void issuesLearningCredential(
      String username, String expectedGivenName, String expectedFamilyName) throws Exception {
    ECKey bindingKey =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWSAlgorithm.ES256)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    String rawCredential =
        IssuanceAgent.builder().build().issueLearningCredential(bindingKey, username, "password");

    SdJwtVc credential = SdJwtVc.parse(rawCredential);
    // achievement_title is a plain claim in the issuer JWT, not selectively disclosable.
    assertThat(
        credential.issuerJwt().getJWTClaimsSet().getClaims(),
        allOf(
            hasEntry("vct", VCT),
            hasEntry("achievement_title", "Foundations of Applied AI in Business")));
    assertThat(
        credential.disclosedClaims(),
        allOf(
            hasEntry("given_name", expectedGivenName),
            hasEntry("family_name", expectedFamilyName),
            hasEntry("assessment_grade", "Excellent")));
  }
}
