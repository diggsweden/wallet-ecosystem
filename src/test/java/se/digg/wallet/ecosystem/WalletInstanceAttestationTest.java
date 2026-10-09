// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class WalletInstanceAttestationTest {

  private final WalletProviderClient walletProvider = new WalletProviderClient();
  private final TrustSourceClient trustSource = new TrustSourceClient();

  @Test
  void createsWalletInstanceAttestation() throws Exception {
    var walletKey = new ECKeyGenerator(Curve.P_256).generate();
    final var beforeRequest = Instant.now().minusSeconds(1);

    var jwt = SignedJWT.parse(walletProvider.getWalletInstanceAttestation(walletKey));

    assertThat(jwt.getHeader().getType().toString(), is("oauth-client-attestation+jwt"));
    assertThat(jwt.getHeader().getAlgorithm(), is(JWSAlgorithm.ES256));
    var certificates = jwt.getHeader().getX509CertChain();
    var certificateFactory = CertificateFactory.getInstance("X.509");
    var signer = (X509Certificate) certificateFactory.generateCertificate(
        new ByteArrayInputStream(certificates.getFirst().decode()));
    var providerCa = (X509Certificate) certificateFactory.generateCertificate(
        new ByteArrayInputStream(trustSource.tryGet("wallet-provider/ca.pem")
            .then().assertThat().statusCode(200).extract().asByteArray()));
    signer.checkValidity();
    signer.verify(providerCa.getPublicKey());
    assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) signer.getPublicKey())), is(true));

    var claims = jwt.getJWTClaimsSet();
    assertThat(claims.toJSONObject().keySet(), is(Set.of(
        "iss", "sub", "iat", "exp", "wallet_name", "wallet_version", "wallet_link",
        "wallet_solution_certification_information", "cnf", "client_status")));
    assertThat(claims.getIssuer(), not(emptyOrNullString()));
    assertThat(claims.getSubject(), not(emptyOrNullString()));
    assertThat(claims.getStringClaim("wallet_name"), not(emptyOrNullString()));
    assertThat(claims.getStringClaim("wallet_version"), not(emptyOrNullString()));
    assertThat(claims.getStringClaim("wallet_link"), not(emptyOrNullString()));
    assertThat(claims.getStringClaim("wallet_solution_certification_information"),
        not(emptyOrNullString()));
    assertThat(claims.getJSONObjectClaim("cnf"),
        is(Map.of("jwk", walletKey.toPublicJWK().toJSONObject())));
    var issuedAt = claims.getIssueTime().toInstant();
    var expiresAt = claims.getExpirationTime().toInstant();
    assertThat(issuedAt.isBefore(beforeRequest), is(false));
    assertThat(issuedAt.isAfter(Instant.now()), is(false));
    var lifetime = Duration.between(issuedAt, expiresAt);
    assertThat(lifetime.isPositive(), is(true));
    assertThat(lifetime.compareTo(Duration.ofHours(24)) < 0, is(true));

    var clientStatus = claims.getJSONObjectClaim("client_status");
    assertThat(clientStatus.keySet(), is(Set.of("status", "exp")));
    var status = (Map<?, ?>) clientStatus.get("status");
    assertThat(status.keySet(), is(Set.of("status_list")));
    var statusList = (Map<?, ?>) status.get("status_list");
    assertThat(statusList.keySet(), is(Set.of("idx", "uri")));
    assertThat(statusList.get("idx"), is(412L));
    var statusExpiration = Instant.ofEpochSecond(((Number) clientStatus.get("exp")).longValue());
    assertThat(Duration.between(issuedAt, statusExpiration).compareTo(Duration.ofDays(31)) >= 0,
        is(true));
    assertThat(statusExpiration.isAfter(expiresAt), is(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "not-json", "null", "{}", "{\"kty\":\"RSA\"}"})
  void rejectsInvalidWalletInstanceAttestationKeys(String invalidJwk) {
    walletProvider.tryGetWalletInstanceAttestation(invalidJwk)
        .then()
        .assertThat()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("status", is(400))
        .body("wallet_instance_attestation", nullValue());
  }

  @Test
  void rejectsPrivateWalletInstanceAttestationKeys() throws Exception {
    var privateKey = new ECKeyGenerator(Curve.P_256).generate();

    walletProvider.tryGetWalletInstanceAttestation(privateKey.toJSONString())
        .then()
        .assertThat()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("detail", is("Private keys are not accepted."))
        .body("wallet_instance_attestation", nullValue());
  }
}
