// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import io.restassured.response.Response;
import java.net.URI;

public class TrustSourceClient {

  private final URI base = ServiceIdentifier.TRUST_SOURCE.getResourceRoot();

  public Response tryGet(String path) {
    return given().baseUri(base.toString()).when().get(path);
  }
}
