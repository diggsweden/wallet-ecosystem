// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.ECKey;
import io.restassured.http.ContentType;
import java.util.UUID;

public class WalletProviderClient {
  private static final String WALLET_PROVIDER_WUA_URL =
      "https://localhost/wallet-provider/wallet-unit-attestation";

  public String getWalletUnitAttestation(ECKey jwk) throws JsonProcessingException {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(
            String.format("""
                { "walletId": "%s", "jwk": %s }""",
                UUID.randomUUID(),
                new ObjectMapper().writeValueAsString(jwk.toPublicJWK().toJSONString())))
        .post(WALLET_PROVIDER_WUA_URL)
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .body()
        .asString();
  }
}
