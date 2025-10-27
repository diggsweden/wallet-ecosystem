// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.SSLConfig.sslConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WalletProviderTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://wallet-provider.wallet.local/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createsWalletUnitAttestation() throws Exception {
    given()
        .when()
        .contentType(ContentType.JSON)
        .body(String.format("""
            { "walletId": "%s", "jwk": %s }""",
            UUID.randomUUID(),
            new ObjectMapper().writeValueAsString(
                new ECKeyGenerator(Curve.P_256).generate().toPublicJWK().toJSONString())))
        .post("https://wallet-provider.wallet.local/wallet-unit-attestation")
        .then()
        .assertThat().statusCode(200).and().body(matchesPattern(
            "^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  private static RequestSpecification given() {
    return RestAssured.given().config(RestAssured.config()
        .sslConfig(sslConfig().relaxedHTTPSValidation())
        .logConfig(logConfig().enableLoggingOfRequestAndResponseIfValidationFails()));
  }
}
