// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TrustSourceTest {

  private static final Pattern JWT_PATTERN =
      Pattern.compile("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");

  private final TrustSourceClient trustSource = new TrustSourceClient();

  @Test
  void servesRevocationList() {
    String body = trustSource.tryGet("revocation-list.pem")
        .then()
        .assertThat().statusCode(200)
        .extract().body().asString();

    assertThat(body, containsString("BEGIN X509 CRL"));
  }

  @Test
  void servesTrustedEntitiesJson() {
    String body = trustSource.tryGet("signed/trusted-entities.json")
        .then()
        .assertThat().statusCode(200)
        .extract().body().asString();

    // The JSON is actually just a raw JWT string
    assertThat(body.trim(), matchesPattern(JWT_PATTERN));
  }
}
