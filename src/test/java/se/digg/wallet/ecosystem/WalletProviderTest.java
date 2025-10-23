// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WalletProviderTest {

  @Test
  void isHealthy() {
    given()
        .relaxedHTTPSValidation()
        .when()
        .get("https://wallet-provider.wallet.local/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createsWalletUnitAttestation() throws Exception {
    given()
        .relaxedHTTPSValidation()
        .log().ifValidationFails()
        .when()
        .contentType(ContentType.JSON)
        .body(String.format("""
            { "walletId": "%s", "jwk": %s }""",
            UUID.randomUUID(),
            new ObjectMapper().writeValueAsString(
                new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONString())))
        .post("https://wallet-provider.wallet.local/wallet-unit-attestation")
        .then()
        .log().ifValidationFails()
        .assertThat().statusCode(200).and().body(matchesPattern(
            "^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }
}
