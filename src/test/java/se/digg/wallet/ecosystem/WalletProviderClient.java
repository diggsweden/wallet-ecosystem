// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
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
import java.util.HashMap;
import java.util.Map;

public class WalletProviderClient {

  private final URI base;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String WUA_URL = "wallet-unit-attestation";
  private static final String KEY_ATTESTATIONS_URL = "key_attestations";

  public WalletProviderClient() {
    this(ServiceIdentifier.WALLET_PROVIDER.getResourceRoot());
  }

  public WalletProviderClient(URI base) {
    this.base = base;
  }

  public Response tryGetHealth() {
    return given()
        .when()
        .get(base.resolve("actuator/health"));
  }

  @Deprecated
  public String getWalletUnitAttestation(ECKey jwk, String nonce) throws JsonProcessingException {
    Map<String, Object> body = new HashMap<>();
    body.put("jwk", jwk.toPublicJWK().toJSONString());
    if (nonce != null) {
      body.put("nonce", nonce);
    }
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(OBJECT_MAPPER.writeValueAsString(body))
        .post(base.resolve(WUA_URL))
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .body()
        .asString();
  }

  public String getKeyAttestation(ECKey jwk, String nonce) throws JsonProcessingException {
    Map<String, Object> body = new HashMap<>();
    body.put("jwk", jwk.toPublicJWK().toJSONString());
    if (nonce != null) {
      body.put("nonce", nonce);
    }
    return given()
        .when()
        .contentType(ContentType.JSON)
        .body(OBJECT_MAPPER.writeValueAsString(body))
        .post(base.resolve(KEY_ATTESTATIONS_URL))
        .then()
        .assertThat()
        .statusCode(200)
        .extract()
        .body()
        .asString();
  }
}
