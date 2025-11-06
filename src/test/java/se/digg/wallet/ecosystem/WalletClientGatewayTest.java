// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WalletClientGatewayTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://wallet-client-gateway.wallet.local/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createsAndGetAttributeAttestation()
      throws Exception {
    var createdId = given().when().contentType(ContentType.JSON).body("""
        {
          "hsmId": "cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49",
          "wuaId": "790acda4-3dec-4d93-8efe-71375109d30e",
          "attestationData": "string"
        }""")
        .header("X-API-KEY", "apikey")
        .post("https://wallet-client-gateway.wallet.local/attribute-attestations")
        .then()
        .assertThat().statusCode(201).and()
        .extract()
        .body()
        .jsonPath()
        .getString("id");

    UUID.fromString(createdId); // verify it's a valid uuid

    given()
        .when()
        .header("X-API-KEY", "apikey")
        .get(
            "https://wallet-client-gateway.wallet.local/attribute-attestations/%s"
                .formatted(createdId))
        .then()
        .assertThat().statusCode(200)
        .and().body("hsmId", equalTo("cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49"));
  }

  @Test
  void createsWalletUnitAttestation() throws Exception {
    given()
        .when()
        .contentType(ContentType.JSON)
        .body("""
            {
              "walletId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "jwk": {
                "kty": "EC",
                "crv": "P-256",
                "x": "1fH0eqXgMMwCIafNaDc1axdCjLlw7zpTLvLWjpPvhEc",
                "y": "5qOejJs7BK-jLingaUTEhBrzP_YPyHfptS5yWE98I40"
              }
            }""")
        .header("X-API-KEY", "apikey")
        .post("https://wallet-client-gateway.wallet.local/wua")
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }
}
