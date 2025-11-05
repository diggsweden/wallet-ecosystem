// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.LogConfig.logConfig;
import static io.restassured.config.SSLConfig.sslConfig;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public final class RestAssuredSugar {
  private RestAssuredSugar() {}

  static RequestSpecification given() {
    return RestAssured.given()
        .config(
            RestAssured.config()
                .sslConfig(sslConfig().relaxedHTTPSValidation())
                .logConfig(logConfig().enableLoggingOfRequestAndResponseIfValidationFails())
                .encoderConfig(
                    encoderConfig()
                        .encodeContentTypeAs("application/jwt", ContentType.TEXT)
                        .appendDefaultContentCharsetToContentTypeIfUndefined(false)));
  }
}
