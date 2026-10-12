// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestMethodOrder(OrderAnnotation.class)
public class WalletClientGatewayTest {

  private static final WalletClientGatewayClient walletClientGateway =
      new WalletClientGatewayClient();
  private static final Path FEATURE_FLAGS =
      Path.of("config/wallet-client-gateway/feature-flags.json");
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
  void getClientConfig_shouldReturnTargetedFeatureFlags() {
    assumeLocalFeatureFlags();

    walletClientGateway.tryGetClientConfig("Android", "2.0.0")
        .then()
        .assertThat()
        .statusCode(200)
        .body("cacheGeneration", equalTo(6))
        .body("features.example", equalTo(true));
  }

  @Test
  void getClientConfig_whenOsDoesNotMatch_shouldReturnFeatureDisabled() {
    assumeLocalFeatureFlags();

    walletClientGateway.tryGetClientConfig("iOS", "2.0.0")
        .then()
        .assertThat()
        .statusCode(200)
        .body("cacheGeneration", equalTo(6))
        .body("features.example", equalTo(false));
  }

  @Test
  void getMaintenanceWindow_whenNoMaintenance_shouldReturnNoContent() {
    assumeLocalFeatureFlags();

    walletClientGateway.tryGetMaintenanceWindow()
        .then()
        .assertThat()
        .statusCode(204);
  }

  @Test
  @Order(100)
  void maintenanceMode_shouldAllowUtilEndpointsAndRejectOtherEndpoints() throws IOException {
    assumeLocalFeatureFlags();

    var originalFlags = Files.readString(FEATURE_FLAGS);
    try {
      Files.writeString(FEATURE_FLAGS, originalFlags.replace(
          "\"gateway.maintenance.enabled\": {\n"
              + "      \"state\": \"ENABLED\",\n"
              + "      \"variants\": { \"on\": true, \"off\": false },\n"
              + "      \"defaultVariant\": \"off\"\n"
              + "    }",
          "\"gateway.maintenance.enabled\": {\n"
              + "      \"state\": \"ENABLED\",\n"
              + "      \"variants\": { \"on\": true, \"off\": false },\n"
              + "      \"defaultVariant\": \"on\"\n"
              + "    }"));

      awaitApiInfoStatus(503);

      walletClientGateway.tryGetMaintenanceWindow()
          .then()
          .assertThat()
          .statusCode(200)
          .body("active", equalTo(true));

      walletClientGateway.tryGetClientConfig("Android", "2.0.0")
          .then()
          .assertThat()
          .statusCode(200)
          .body("maintenance.active", equalTo(true));

      walletClientGateway.tryGetApiInfo()
          .then()
          .assertThat()
          .statusCode(503)
          .header("Content-Type", startsWith("application/problem+json"))
          .body("status", equalTo(503))
          .body("title", equalTo("Service under maintenance"))
          .body("type", equalTo("/problem-details/maintenance"));
    } finally {
      Files.writeString(FEATURE_FLAGS, originalFlags);
      awaitApiInfoStatus(200);
    }
  }

  @Test
  void createAccount_should_return_accountId() throws Exception {
    var ecKey = generateKey();
    var accountRequestBody = stubAccountV0Request(ecKey);
    var accountId = walletClientGateway.createAccount(accountRequestBody);
    assertThat("accountId should be UUID", UUID.fromString(accountId), instanceOf(UUID.class));
  }

  @Test
  void addWalletKey_should_succeed() throws Exception {
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

  private static void assumeLocalFeatureFlags() {
    assumeTrue(Boolean.parseBoolean(
        Property.WALLET_CLIENT_GATEWAY_LOCAL_FEATURE_FLAGS_TESTING_ENABLED.getValue()),
        "Requires the gateway to use the local feature-flags.json fixture");
  }

  private static void awaitApiInfoStatus(int expectedStatus) {
    int lastStatus = 0;
    for (int attempt = 0; attempt < 40; attempt++) {
      var response = walletClientGateway.tryGetApiInfo();
      lastStatus = response.statusCode();
      if (lastStatus == expectedStatus) {
        return;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("Interrupted while waiting for /api-info to return status " + expectedStatus);
      }
    }
    fail("Expected /api-info to return status " + expectedStatus + " but last status was "
        + lastStatus);
  }
}
