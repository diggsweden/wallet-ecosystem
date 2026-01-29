// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.ECKey;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.util.UUID;

public class WalletProviderClient {

  private final URI base = ServiceIdentifier.WALLET_PROVIDER.getResourceRoot();

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  public String getWalletUnitAttestation(ECKey jwk) throws JsonProcessingException {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(
            String.format("""
                { "walletId": "%s", "jwk": %s }""",
                UUID.randomUUID(),
                new ObjectMapper().writeValueAsString(jwk.toPublicJWK().toJSONString())))
        .post(base.resolve("wallet-unit-attestation"))
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .body()
        .asString();
  }

  public String getWalletUnitAttestationV2(ECKey jwk, String nonce) throws JsonProcessingException {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(
            String.format("""
                {
                  "walletId": "%s",
                  "jwk": %s,
                  "nonce": "%s"
                }""",
                UUID.randomUUID(),
                new ObjectMapper().writeValueAsString(jwk.toPublicJWK().toJSONString()),
                nonce))
        .post(base.resolve("wallet-unit-attestation/v2"))
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .body()
        .asString();
  }
}
