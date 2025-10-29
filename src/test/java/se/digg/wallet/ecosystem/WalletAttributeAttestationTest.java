// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WalletAttributeAttestationTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://wallet-attribute-attestation.wallet.local/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createsAndGetAttestation()
      throws Exception {
    var createdId = given().when().contentType(ContentType.JSON).body("""
        {
          "hsmId": "cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49",
          "wuaId": "790acda4-3dec-4d93-8efe-71375109d30e",
          "attestationData": "string"
        }""")
        .post("https://wallet-attribute-attestation.wallet.local/attestation")
        .then()
        .assertThat().statusCode(201).and()
        .extract()
        .body()
        .jsonPath()
        .getString("id");

    UUID.fromString(createdId); // verify it's a valid uuid

    given()
        .when()
        .get(
            "https://wallet-attribute-attestation.wallet.local/attestation/%s".formatted(createdId))
        .then()
        .assertThat().statusCode(200)
        .and().body("hsmId", equalTo("cbe80ad0-6a7d-4a5a-9891-8b4e95fa4d49"));
  }
}
