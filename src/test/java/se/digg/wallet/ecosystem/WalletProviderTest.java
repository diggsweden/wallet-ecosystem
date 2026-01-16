// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;

public class WalletProviderTest {

  private final WalletProviderClient walletProvider = new WalletProviderClient();

  @Test
  void isHealthy() {
    walletProvider.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createsWalletUnitAttestation() throws Exception {
    String wua = walletProvider.getWalletUnitAttestation(
        new ECKeyGenerator(Curve.P_256).generate(),
        "nonce");

    assertThat(wua, matchesPattern(
        "^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }
}
