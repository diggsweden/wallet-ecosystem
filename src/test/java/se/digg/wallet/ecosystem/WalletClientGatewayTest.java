// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.fail;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
public class WalletClientGatewayTest {

  private static final WalletClientGatewayClient walletClientGateway =
      new WalletClientGatewayClient();
  private static String session;

  @BeforeAll
  static void beforeAll() throws Exception {
    var ecKey = generateKey();
    var accountId = createAccount(ecKey);
    var nonce = walletClientGateway.initChallenge(accountId, ecKey.getKeyID());
    var signedJwt = createSignedJwt(ecKey, nonce);
    session = walletClientGateway.respondToChallenge(signedJwt);
  }

  @Test
  void appActuatorHealth_status_shouldReturnUp() {
    walletClientGateway.tryGetHealth()
        .then()
        .assertThat().statusCode(200)
        .and().body("status", equalTo("UP"));
  }

  @Test
  void createAccount_should_return_accountId() throws Exception {
    var ecKey = generateKey();
    var accountRequestBody = stubAccountV0Request(ecKey);
    var accountId = walletClientGateway.createAccount(accountRequestBody);
    assertThat("accountId should be UUID", UUID.fromString(accountId), instanceOf(UUID.class));
  }

  @Test
  void addWalletKey_should_return_201() throws Exception {
    var walletKey = generateKey();
    walletClientGateway.addWalletKey(session, walletKey.toPublicJWK().toJSONString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"nonce"})
  @NullSource
  void createWalletUnitAttestation(String wuaNonce) throws Exception {
    var ecKey = generateKey();
    var accountId = createAccount(ecKey);
    var nonce = walletClientGateway.initChallenge(accountId, ecKey.getKeyID());
    var signedJwt = createSignedJwt(ecKey, nonce);
    var session = walletClientGateway.respondToChallenge(signedJwt);
    var walletKey = generateKey();
    walletClientGateway.addWalletKey(session, walletKey.toPublicJWK().toJSONString());

    walletClientGateway.tryCreateWalletUnitAttestation(session, wuaNonce)
        .then()
        .assertThat().statusCode(201).and()
        .body("jwt", matchesPattern("^[A-Za-z0-9]+\\.[A-Za-z0-9]+\\.[A-Za-z0-9\\-_]+$"));
  }

  @Test
  void createsWalletUnitAttestation_withEmptyNonce_shouldGiveBadRequest() {
    String emptyNonce = "";
    walletClientGateway.tryCreateWalletUnitAttestation(session, emptyNonce)
        .then()
        .assertThat().statusCode(400);
  }

  @Disabled
  void saveDeviceState() {
    var request = """
        {
          "deviceKey": {
            "kty": "EC",
            "kid": "%s",
            "alg": null,
            "use": null,
            "crv": "P-256",
            "x": "1fH0eqXgMMwCIafNaDc1axdCjLlw7zpTLvLWjpPvhEc",
            "y": "5qOejJs7BK-jLingaUTEhBrzP_YPyHfptS5yWE98I40"
          },
          "ttl": "P30D"
        }""".formatted(UUID.randomUUID().toString());

    walletClientGateway.saveDeviceState(session, request)
        .then()
        .assertThat()
        .statusCode(201);
  }

  @Disabled
  void malformedJwsInOuterRequest() {
    var request = """
        {
          "outerRequestJws": "malformed-outer-request-jws",
          "clientId": "%s",
          "stateJws": "the-state-jws"
        }""".formatted(UUID.randomUUID().toString());

    walletClientGateway.createHsmRequest(session, request)
        .then()
        .assertThat()
        .statusCode(500);
  }

  @Disabled
  void nonExistingHsmResultReturnsAccepted() {
    var nonExistingId = UUID.randomUUID().toString();
    walletClientGateway.getAsyncHsmResult(session, nonExistingId)
        .then()
        .assertThat()
        .statusCode(202);
  }

  static ECKey generateKey() {
    try {
      return new ECKeyGenerator(Curve.P_256)
          .keyID(UUID.randomUUID().toString())
          .algorithm(Algorithm.NONE)
          .keyUse(KeyUse.SIGNATURE)
          .generate();
    } catch (Exception e) {
      fail("Unable to generate key");
      return null;
    }
  }

  private static String createAccount(ECKey ecKey) {
    var accountRequestBody = stubAccountV0Request(ecKey);
    return walletClientGateway.createAccount(accountRequestBody);
  }

  private static String stubAccountV0Request(ECKey ecKey) {
    return """
        {
          "deviceKey": %s,
          "personalIdentityNumber": "%s"
        }""".formatted(ecKey.toPublicJWK().toJSONString(), randomPersonalIdentityNumber());
  }

  private static String randomPersonalIdentityNumber() {
    return Long.toString(
        ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L));
  }

  private static String createSignedJwt(ECKey ecJwk, String nonce)
      throws JOSEException {
    var claims = new JWTClaimsSet.Builder()
        .claim("nonce", nonce)
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(ecJwk.getKeyID()).build();
    var signedJwt = new SignedJWT(header, claims);

    signedJwt.sign(new ECDSASigner(ecJwk));

    return signedJwt.serialize();
  }
}
