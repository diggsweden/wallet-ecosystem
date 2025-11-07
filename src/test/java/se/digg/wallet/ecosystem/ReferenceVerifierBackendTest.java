// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.is;
import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import org.junit.jupiter.api.Test;

public class ReferenceVerifierBackendTest {

  @Test
  void isHealthy() {
    given()
        .when()
        .get("https://localhost/refimpl-verifier-backend/actuator/health")
        .then()
        .assertThat().statusCode(200)
        .and().body("status", is("UP"));
  }
}
