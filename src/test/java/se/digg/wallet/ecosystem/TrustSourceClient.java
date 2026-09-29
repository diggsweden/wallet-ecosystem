// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static se.digg.wallet.ecosystem.RestAssuredSugar.given;

import com.nimbusds.jwt.SignedJWT;
import io.restassured.response.Response;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;

public class TrustSourceClient {

  private static final String LOTE_RESOURCE_PATH = "signed/trusted-entities.json";
  private static final String TRUST_SOURCE_CA_RESOURCE_PATH = "trust-source/ca.pem";
  private final URI base;

  public TrustSourceClient() {
    this(ServiceIdentifier.TRUST_SOURCE.getResourceRoot());
  }

  public TrustSourceClient(URI base) {
    this.base = base;
  }

  public Response tryGet(String path) {
    return given().when().get(base.resolve(path));
  }

  public SignedJWT fetchLote() throws ParseException {
    return SignedJWT.parse(tryGet(LOTE_RESOURCE_PATH)
        .then().assertThat().statusCode(200)
        .extract().body().asString().trim());
  }

  public X509Certificate fetchTrustSourceCa() {
    var pem = tryGet(TRUST_SOURCE_CA_RESOURCE_PATH)
        .then().assertThat().statusCode(200)
        .extract().body().asByteArray();
    try {
      return (X509Certificate) CertificateFactory.getInstance("X.509")
          .generateCertificate(new ByteArrayInputStream(pem));
    } catch (CertificateException e) {
      throw new IllegalArgumentException("Trust-source CA certificate is malformed", e);
    }
  }
}
