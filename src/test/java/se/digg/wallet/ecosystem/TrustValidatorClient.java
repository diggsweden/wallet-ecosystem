// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.response.Response;

public class TrustValidatorClient {

  private final ServiceIdentifier base = ServiceIdentifier.TRUST_VALIDATOR;

  public Response tryGetHealth() {
    return given().baseUri(base.toString()).when().get("actuator/health/");
  }
}
