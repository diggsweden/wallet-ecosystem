// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.restassured.response.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@DisplayNameGeneration(DisplayNameGenerator.Standard.class)
class VerifierBackendTest {
  private static final String dcqlId = UUID.randomUUID().toString();
  private final String nonce = UUID.randomUUID().toString();
  private VerifierBackendClient verifierBackend;
  private IssuanceAgent issuer;
  private ECKey bindingKey;

  @BeforeEach
  void setUp() throws JOSEException {
    verifierBackend = new VerifierBackendClient();
    issuer = new IssuanceAgent();
    bindingKey = new ECKeyGenerator(Curve.P_256)
        .algorithm(JWSAlgorithm.ES256)
        .keyUse(KeyUse.SIGNATURE)
        .generate();
  }

  @Test
  @DisabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_SKIP_TESTS_FOR_VERIFIER_BACKEND_HEALTH",
      matches = "true")
  void isHealthy() {
    verifierBackend
        .tryGetHealth()
        .then()
        .assertThat()
        .statusCode(200)
        .and()
        .body("status", equalTo("UP"));
  }

  @Test
  void createsPresentationRequest() {
    VerifierPresentationResponse presentationResponse =
        verifierBackend.createPresentationRequestByValue(dcqlId);

    assertNotNull(presentationResponse);
    assertThat(presentationResponse.transaction_id(), notNullValue());
    assertThat(presentationResponse.request(), notNullValue());
    assertThat(presentationResponse.client_id(), is(VerifierBackendClient.VERIFIER_AUDIENCE));
  }

  @Test
  void returnsPresentationEvents() {
    VerifierPresentationResponse presentationResponse =
        verifierBackend.createPresentationRequestByValue(dcqlId);
    String transactionId = presentationResponse.transaction_id();
    Response response = verifierBackend.getPresentationEvents(transactionId);

    assertThat(response.getStatusCode(), is(200));
    List<String> events = response.jsonPath().getList("events.event");
    assertThat(events, is(List.of("Transaction initialized")));
  }

  @Test
  void acceptsValidSdJwtVcCredential() throws Exception {
    // 1. Get credential
    String sdJwtVc = issuer.issuePidCredential(bindingKey, "tneal", "password");

    // 2. Create Key Binding JWT
    String vpToken = VerifiablePresentationToken.asString(sdJwtVc, bindingKey, nonce);

    // 3. Validate SD-JWT VC using the utility endpoint
    verifierBackend
        .validateSdJwtVc(vpToken, nonce)
        .then()
        .assertThat().statusCode(200)
        .and().body("vct", is("urn:eudi:pid:1"))
        .and().body("iss", is(ServiceIdentifier.PID_ISSUER.toString()))
        .and().body("family_name", is("Neal"))
        .and().body("issuing_authority", is("SE Administrative authority"));
  }

  @Test
  @EnabledIfEnvironmentVariable(
      named = "DIGG_WALLET_ECOSYSTEM_INCLUDE_TESTS_WITH_UNTRUSTED_ISSUER",
      matches = "true")
  void rejectsUntrustedPidIssuer() throws Exception {
    IssuanceAgent untrustedIssuer = IssuanceAgent.untrusted();

    // 1. Get credential
    String sdJwtVc = untrustedIssuer.issuePidCredential(bindingKey, "tneal", "password");

    // 2. Create Key Binding JWT
    String vpToken = VerifiablePresentationToken.asString(sdJwtVc, bindingKey, nonce);

    // 3. Validate SD-JWT VC using the utility endpoint
    verifierBackend.validateSdJwtVc(vpToken, nonce)
        .then()
        .assertThat().statusCode(400)
        .and().body(".", hasSize(1))
        .and().body("[0].error", is("IssuerCertificateIsNotTrusted"))
        .and().body("[0].description", is("sd-jwt vc issuer certificate is not trusted"));

    // 4. Print data so that it can be reused from the test below
    System.out.format("Key: %s%n", bindingKey.toJSONString());
    System.out.format("Credential: %s%n", sdJwtVc);
  }

  @Test
  void rejectsCredentialFromUntrustedSource() throws Exception {
    ECKey keyUsedToSignUntrustedCredentials = ECKey.parse("""
        {
          "kty": "EC",
          "d": "sxHKxkZk-FI9o_gHVIKR5RCLvQCjdf9Wsc1TudxXqRo",
          "use": "sig",
          "crv": "P-256",
          "x": "-Z4csUWBEKRd1gBwx1kVWWk6FzsdV6UZ7aIBpCldMN4",
          "y": "fHs6fpYQzlMP8dKLnn_37uqr-ugjPkZfruAjpWuIsIU",
          "alg": "ES256"
        }
        """);

    String credentialsIssuedByUntrustedSource =
        "eyJ4NWMiOlsiTUlJQjZEQ0NBWTJnQXdJQkFnSVViQlBQaE1SQ1BqYWhIUCtKQjcyNUI0Mkg5Yll3Q2dZSUtvWkl6ajBFQXdJd1JERUxNQWtHQTFVRUJoTUNVMFV4RFRBTEJnTlZCQW9NQkVSSlIwY3hKakFrQmdOVkJBTU1IVVJKUjBjZ1YyRnNiR1YwSUVWamIzTjVjM1JsYlNCU2IyOTBJRU5CTUI0WERUSTJNRE16TURBNU1qSXhObG9YRFRJNE1EY3dNakE1TWpJeE5sb3dQVEVMTUFrR0ExVUVCaE1DVTBVeERUQUxCZ05WQkFvTUJFUkpSMGN4SHpBZEJnTlZCQU1NRmxCSlJDQkpjM04xWlhJZ0tFVmpiM041YzNSbGJTa3dXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUUVvZjFnczhHT05KajMyUGlMWE1Nbjl5VnNNY2F2aWJCTkdhNVZMVUZscHZDSEEzR0FaQ0ZUdGNnQ0IvNyt6MDNLaXR4Z3pvNk90M3RRNHVHN0lwR0NvMlF3WWpBZ0JnTlZIUkVFR1RBWGdnbHNiMk5oYkdodmMzU0NDbkJwWkMxcGMzTjFaWEl3SFFZRFZSME9CQllFRkErTXNCK2JleER0YSsrRTFqbU9kY2NFSHh1ak1COEdBMVVkSXdRWU1CYUFGSWxRcUhlcUF3a1ZNNlBNM0x3Y3RRSVIrUzFDTUFvR0NDcUdTTTQ5QkFNQ0Ewa0FNRVlDSVFEUUc5dWc5ZVpJelRCRGZHWm8zbnlEa3J1UkQ5N0xoOHdna0pLYmJNQWhKQUloQUowUHpjbmJLVXhSR3NCTC9LSE52eXJDYVdBMzgzcllZV3hXZnozV0VFK1IiXSwia2lkIjoicGlkX2lzc3VlciIsInR5cCI6ImRjK3NkLWp3dCIsImFsZyI6IkVTMjU2In0.eyJfc2QiOlsiUzFidmhELUZQY05rb1ZVdDlJcFotZXJHSTdSeHBXZkdRbzJ3Z0NVTUh6ayIsInpscFNHYm16bTFaclZ5ZjFReFpGRW1jZEFSNkV1bDhLZXEzcmFocDZsNjgiLCIzMVNqeGZYcTVKWWgzS25fMzRRX0VPNTR0aFRDR2lVdTk3d0JLSmtBNVljIiwic2N3REVYMi1NQVcza3FsTHBKMFBZUHo0MTBNU0x2emJnR1pRdWxBTGZUbyIsImk4TGtEMU93YnVfZi1FaTQwR3lYNkVzZlZHZjQ5Ql83SjlGUktlVklxNFEiLCJMODF3LU45b0tVQVZrb1J2cTg3X0c2YWJNcDhGb203cjlkTUV2eVdjZURJIiwiLV9YTEhSM1ZFRU10M3pkRWN5aVlzQXRUSjBRc2xNM2RyVnNMYjhsdXoyQSIsInA0Z2F2X0JXWUJ5S3RwOUtrRmlHSkR6ZWpHaXlVQ2RxNUNKa01mWW5QNGciLCJva19YM2Y1Tkg4a3V3V0E2emJIMzNhdW1SUUVRTUJjVkFCeDhZRm5BdGFJIiwiejdPYVFWLWFTaGdocGdUcWRRUzB1NzdfNEJONFZ6ZWxsbzRyRnlnM25ZZyIsInRQZFNobmNxSEJqYkxFSGNsdTRTZEx4X2RIem5DaXBiSlAxVmVpMnFxRlEiLCJQYTQwRDRTVFVrOEJZbHY2clBUOEwwNWkzdjVJeW1DQnBOdDgyUi1ya2p3IiwiaFU3dE5EaWY4OU0yUEVjSWMyeUsxMHlQTlFLLWJyVDNfeVd5ZmYxWGV0MCIsIkw4eHBTaWFhbEprVlNYUFV3S19aZG0xcTRBZ053MUkxUFoxYkdUZXpGajAiXSwibmJmIjoxNzc0ODYzNjkxLCJ2Y3QiOiJ1cm46ZXVkaTpwaWQ6MSIsIl9zZF9hbGciOiJzaGEtMjU2IiwiaXNzIjoiaHR0cHM6Ly9sb2NhbGhvc3QvcGlkLWlzc3VlciIsImNuZiI6eyJqd2siOnsia3R5IjoiRUMiLCJ1c2UiOiJzaWciLCJjcnYiOiJQLTI1NiIsIngiOiItWjRjc1VXQkVLUmQxZ0J3eDFrVldXazZGenNkVjZVWjdhSUJwQ2xkTU40IiwieSI6ImZIczZmcFlRemxNUDhkS0xubl8zN3Vxci11Z2pQa1pmcnVBanBXdUlzSVUiLCJhbGciOiJFUzI1NiJ9fSwiZXhwIjoyMDM0MDYzNjcxLCJpYXQiOjE3NzQ4NjM2NzF9.vUuY-cRF2dPzf9woVYKbBDybBFwFzeY5z50Axe5fR8-Pms2dl-0G2O0V3PuBQuBCiA79Je-jzIyXOIbGk3R5tA~WyJwS1ZmYkxKdlQ4ZDZEVm9YTVdOSE93IiwiZmFtaWx5X25hbWUiLCJOZWFsIl0~WyJxVHhNRlh0ZzdvcWpkNTRoYldKMWh3IiwiZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyJDMUo3OWJid0xkRDR0UXptYXJOajNnIiwiYmlydGhkYXRlIiwiMTk1NS0wNC0xMiJd~WyJLanRNbXJ0U0FGZ21yTkRLNnJwVF93IiwibG9jYWxpdHkiLCIxMDEgVHJhdW5lciJd~WyJwU0dadzN5RmlFUk45QTJlR0dGSjVnIiwicGxhY2Vfb2ZfYmlydGgiLHsiX3NkIjpbImRaczdYbWI1N2hCdTBmcnA2RmN1ejh4VFFteTgwTzdSTVpkQ1pUTFRVazgiXX1d~WyJtQkpZOUNOVzMtSmc2clRSTExJcDVnIiwiQVQiXQ~WyJhZ20wRHVfSS1kcUo1a3h4UlNCZjhnIiwibmF0aW9uYWxpdGllcyIsW3siLi4uIjoiRGw1Z2RmN0lqTllUekR1SWYzUTFVMmhueXFVSE9iSTFJRGZfOGJFNksySSJ9XV0~WyJ6MkE3b3NjQWNGQ2ctY3ZEX0lFbTZ3Iiwic3RyZWV0X2FkZHJlc3MiLCJUcmF1bmVyIl0~WyJBMWZSbi0xR3I3UUJ0RTRRcDBpNEt3IiwibG9jYWxpdHkiLCJHZW1laW5kZSBCaWJlcmJhY2giXQ~WyI2MXdFQWtFYm4zc2M0NEZ0blhfeHNRIiwicG9zdGFsX2NvZGUiLCIzMzMxIl0~WyJfN0kxYkp1NnU1WWQ3THg1VXpVSkRnIiwiY291bnRyeSIsIkFUIl0~WyJiaUI4U2ZsWFRUZktzMVMyQUIzdk5BIiwiYWRkcmVzcyIseyJfc2QiOlsiOTltSTIwVkZGZk1LZDVCMk92R1VMVDBBTE9nNWc3dWZhaDNneWpUcm9kTSIsImFCb21EX1ZMelRZWlQ5cG1JQ1BGdDhkTVYxRGNPMXNEamhsYWdubUx3M28iLCJ6cGQxQ2dlVlZIQ1djTkVpRGdxQjNOZzRjWFE4ZkhVeVNNUVZaX19kREM0IiwiYUZWa01hXzNYeWFlenJxcU1ZcUhNZDBoTG1MNmRXUlo2bHBRRTdIV1RTWSJdfV0~WyJZSnUyVHROODNjMTZHRWw2TjRILU9RIiwicGVyc29uYWxfYWRtaW5pc3RyYXRpdmVfbnVtYmVyIiwiYzAwODVjMzgtYjlhNi00NTljLWFmMDEtNDYyMzc1MDhjOTg1Il0~WyJ1azEwSS1rUmJSeE80Mm9FZ01LWjF3IiwiZW1haWwiLCJ0eWxlci5uZWFsQGV4YW1wbGUuY29tIl0~WyI0UllQR0hiWmdiTVg1RXNCZlJQY1VnIiwiZGF0ZV9vZl9leHBpcnkiLCIyMDI2LTA3LTA4Il0~WyJsTmppVmRRS3FqVDllaXBhLWRPQThnIiwiaXNzdWluZ19hdXRob3JpdHkiLCJTRSBBZG1pbmlzdHJhdGl2ZSBhdXRob3JpdHkiXQ~WyJXSnBPR3ZXS3dkODdNOWd2NmdGaktRIiwiaXNzdWluZ19jb3VudHJ5IiwiU0UiXQ~WyJRT01pRTRKZno3Yk5aZW1CTzFGY1RnIiwiZG9jdW1lbnRfbnVtYmVyIiwiYmRhYmJiMjEtZWRmOC00NDYyLWExZjAtOWFkNTMwYmI2YTgyIl0~WyI2LUgxMUpjMkdmWmhzcjNGY0VOM1p3IiwiaXNzdWluZ19qdXJpc2RpY3Rpb24iLCJTRS1ZIl0~WyI2aUR1OWNZcXhQdVdyZC02MTFNWEJRIiwiZGF0ZV9vZl9pc3N1YW5jZSIsIjIwMjYtMDMtMzAiXQ~";

    // 2. Create Key Binding JWT
    String vpToken = VerifiablePresentationToken.asString(
        credentialsIssuedByUntrustedSource, keyUsedToSignUntrustedCredentials, nonce);

    // 3. Validate SD-JWT VC using the utility endpoint
    verifierBackend.validateSdJwtVc(vpToken, nonce)
        .then()
        .assertThat().statusCode(400)
        .and().body(".", hasSize(1))
        .and().body("[0].error", is("IssuerCertificateIsNotTrusted"))
        .and().body("[0].description", is("sd-jwt vc issuer certificate is not trusted"));
  }
}
