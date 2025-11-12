// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class EndToEndTest {

  private final KeycloakClient keycloak = new KeycloakClient();
  private final WalletProviderClient walletProvider = new WalletProviderClient();
  private final PidIssuerClient pidIssuer = new PidIssuerClient();

  @Test
  void getCredential() throws Exception {
    ECKey userJwk = new ECKeyGenerator(Curve.P_256).generate();

    // 1. Get access token for user
    String accessToken = keycloak.getDpopAccessToken("pid-issuer-realm", userJwk, Map.of(
        "grant_type", "password",
        "client_id", "wallet-dev",
        "username", "tneal",
        "password", "password",
        "scope", "openid eu.europa.ec.eudi.pid_vc_sd_jwt",
        "role", "user"));

    // 2. Create JWK for wallet
    ECKey jwk =
        new ECKeyGenerator(Curve.P_256)
            .algorithm(JWEAlgorithm.ECDH_ES)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();

    // 3. Get WUA
    String walletAttestation = walletProvider.getWalletUnitAttestation(jwk);

    // 4. Get nonce
    String nonce = pidIssuer.getNonce(accessToken, userJwk);

    // 5. Create proof
    String proof = createProof(jwk, walletAttestation, nonce);

    ECKey pidIssuerCredentialRequestEncryptionKey = pidIssuer.getCredentialRequestEncryptionKey();

    // 6. Get credential from issuer
    Map<String, Object> payloadJson = pidIssuer.issueCredentials(
        accessToken, userJwk, jwk, proof, pidIssuerCredentialRequestEncryptionKey).toJSONObject();

    assertEquals(pidIssuer.getName(), payloadJson.get("iss"));

    var credentials = payloadJson.get("credentials");
    assertThat(credentials, instanceOf(List.class));
    assertThat((List<?>) credentials, not(empty()));
  }

  private String createProof(ECKey jwk, String wua, String nonce) throws JOSEException {
    JWSHeader header =
        new JWSHeader.Builder(JWSAlgorithm.ES256)
            .type(new JOSEObjectType("openid4vci-proof+jwt"))
            .jwk(jwk.toPublicJWK())
            .build();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(jwk.toPublicJWK().toString())
            .audience(pidIssuer.getName())
            .issueTime(Date.from(Instant.now()))
            .claim("nonce", nonce)
            .claim("wua", wua)
            .build();

    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(new ECDSASigner(jwk));
    return jwt.serialize();
  }
}
