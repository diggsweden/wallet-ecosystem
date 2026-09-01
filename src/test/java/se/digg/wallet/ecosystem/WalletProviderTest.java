// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("The wallet provider")
public class WalletProviderTest {

  private final WalletProviderClient walletProvider = new WalletProviderClient();

  @Test
  void isHealthy() {
    walletProvider.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonce", ""})
  @NullSource
  void createsWalletUnitAttestation(String nonce) throws Exception {
    String wua = walletProvider.getWalletUnitAttestation(
        new ECKeyGenerator(Curve.P_256).generate(),
        nonce);

    assertThat(wua, matchesPattern(
        "^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));

    // Verify WUA contains the injected key_storage_status
    com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(wua);
    org.junit.jupiter.api.Assertions.assertNotNull(
        jwt.getJWTClaimsSet().getClaim("key_storage_status"),
        "WUA must contain the 'key_storage_status' claim");
  }
}
