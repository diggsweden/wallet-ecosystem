// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the Trust Validator service client and API interactions.
 */
public class TrustValidatorTest {

  private static final TrustValidatorClient trustValidator = new TrustValidatorClient();
  private static final IssuanceAgent issuanceAgent = new IssuanceAgent();

  @BeforeAll
  static void ensureReady() {
    TrustValidatorClient.waitUntilReady();
  }

  @Test
  void isHealthy() {
    trustValidator.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", is("UP"));
  }

  @Test
  void validatesTrustedIssuerCredential() throws Exception {
    // 1. Create a new key for a "wallet"
    ECKey walletKey = new ECKeyGenerator(Curve.P_256)
        .algorithm(JWSAlgorithm.ES256)
        .keyUse(KeyUse.SIGNATURE)
        .generate();

    // 2. Issue a credential (PID) using IssuanceAgent
    String sdJwtVc = issuanceAgent.issuePidCredential(walletKey, "tneal", "password");

    // 3. Extract the certificate chain from the credential (x5c header)
    SignedJWT signedJwt = SignedJWT.parse(sdJwtVc.split("~")[0]);
    List<String> chain = signedJwt.getHeader().getX509CertChain().stream()
        .map(com.nimbusds.jose.util.Base64::toString)
        .toList();

    // 4. Validate the chain with the Trust Validator
    trustValidator.tryValidateCertificateChain(chain, "PID")
        .then()
        .assertThat().statusCode(200)
        .and().body("trusted", is(true));
  }
}
