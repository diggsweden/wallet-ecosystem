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

public class WalletProviderClient {

  private final URI base;

  private static final String WUA_V2_URL = "wallet-unit-attestation/v2";

  private final String path;

  public WalletProviderClient() {
    this(WUA_V2_URL);
  }

  public WalletProviderClient(URI base) {
    this(base, WUA_V2_URL);
  }

  public WalletProviderClient(String path) {
    this(ServiceIdentifier.WALLET_PROVIDER.getResourceRoot(), path);
  }

  public WalletProviderClient(URI base, String path) {
    this.base = base;
    this.path = path;
  }

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  public String getWalletUnitAttestation(ECKey jwk, String nonce) throws JsonProcessingException {
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(
            String.format("""
                {
                  "jwk": %s,
                  "nonce": "%s"
                }""",
                new ObjectMapper().writeValueAsString(jwk.toPublicJWK().toJSONString()),
                nonce))
        .post(base.resolve(path))
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .body()
        .asString();
  }
}
