// SPDX-FileCopyrightText: 2025 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import org.junit.jupiter.api.Test;

public class TraefikTest {

  @Test
  void respondsToPing() {
    given()
        .when()
        .get("http://localhost:8080/ping")
        .then()
        .assertThat().statusCode(200)
        .and().body(is("OK"));
  }

  @Test
  void isHealthy() {
    given()
        .when()
        .get("http://localhost:8080/dashboard")
        .then()
        .assertThat().statusCode(200)
        .and().body("html.head.title", is("Traefik Proxy"));
  }
}
