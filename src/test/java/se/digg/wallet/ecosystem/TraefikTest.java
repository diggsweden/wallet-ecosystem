// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.Matchers.containsString;
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
        .and().body("html.head.title", is("Traefik"))
        .and().body(containsString("<div id=q-app>"));
  }
}
