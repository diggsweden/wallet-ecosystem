// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;
import static se.digg.wallet.ecosystem.PersonalIdentityNumberUtil.getRandomPersonalId;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import io.restassured.http.ContentType;

public class WalletAccountTest {

  private static final String BASE = "https://localhost/wallet-account";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Test
  void isHealthy() {
    given()
        .when()
        .get(BASE + "/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createAccount_returnsAccountWithId() {
    given()
        .contentType(ContentType.JSON).body(accountBody(getRandomPersonalId(), "device-kid"))
        .when().post(BASE + "/v0/accounts")
        .then().statusCode(201)
        .body("id", matchesPattern(UUID_PATTERN));
  }

  @Test
  void getAccount_returnsCreatedAccount() {
    String pid = getRandomPersonalId();
    String id = createAccount(pid, "device-kid");

    given()
        .when().get(BASE + "/v0/accounts/" + id)
        .then().statusCode(200)
        .body("id", equalTo(id))
        .body("personalIdentityNumber", equalTo(pid))
        .body("email", equalTo("test@hej.se"))
        .body("deviceKey.kid", equalTo("device-kid"));
  }

  @Test
  void addWalletKey_isReadableOnAccount() {
    String id = createAccount(getRandomPersonalId(), "device-kid");
    String kid = "wallet-" + UUID.randomUUID();

    given()
        .contentType(ContentType.JSON).body(keyBody(kid))
        .when().post(BASE + "/v0/accounts/" + id + "/wallet-keys")
        .then().statusCode(201);

    given()
        .when().get(BASE + "/v0/accounts/" + id + "/wallet-keys")
        .then().statusCode(200)
        .body("items.kid", hasItem(kid));
  }

  // TODO fix datatype mismatch between api and database Could not convert 'java.lang.String' to
  // 'java.sql.Blob' using 'org.hibernat
  /*@Test
  void addSecurityEnvelope_isReadableOnAccount() {
    String id = createAccount(getRandomPersonalId(), "device-kid");
    String content = "envelope-" + UUID.randomUUID();

    given()
        .contentType(ContentType.JSON).body("""
            { "content": "%s" }""".formatted(content))
        .when().post(BASE + "/v0/accounts/" + id + "/security-envelopes")
        .then().statusCode(201);

    given()
        .when().get(BASE + "/v0/accounts/" + id + "/security-envelopes")
        .then().statusCode(200)
        .body("items.content", hasItem(content));
  }*/

  private static String createAccount(String pid, String kid) {
    return given()
        .contentType(ContentType.JSON).body(accountBody(pid, kid))
        .when().post(BASE + "/v0/accounts")
        .then().statusCode(201)
        .extract().jsonPath().getString("id");
  }

  private static String accountBody(String pid, String kid) {
    return """
        {
          "personalIdentityNumber": "%s",
          "email": "test@hej.se",
          "phoneNumber": "070-123 45 67",
          "deviceKey": %s
        }""".formatted(pid, keyBody(kid));
  }

  private static String keyBody(String kid) {
    try {
      return new ECKeyGenerator(Curve.P_256)
          .keyID(kid)
          .algorithm(Algorithm.NONE)
          .keyUse(KeyUse.SIGNATURE)
          .generate().toJSONString();
    } catch (JOSEException ex) {
      return "";
    }
  }
}
