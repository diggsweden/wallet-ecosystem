// SPDX-FileCopyrightText: 2025 2025 The Wallet Ecosystem Authors
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

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
          "d": "ZUlmzCVeTzJT6CoRn3hlocAefrzzRFRj6ue8cbnDYcM",
          "use": "sig",
          "crv": "P-256",
          "x": "B1pfB0zv9fpD1vyVaDAP8pGatOjM9XOUx03jp0ww7Bs",
          "y": "As1B1zwNlPx7C2qPx8eOghSnVqrliRl3sweHkZRjrSg",
          "alg": "ES256"
        }
        """);

    String credentialsIssuedByUntrustedSource =
        "eyJ4NWMiOlsiTUlJQjVqQ0NBWTJnQXdJQkFnSVVJZzQ1bUJ4YngxNzViUWtzUTduV0tzaHVvbDR3Q2dZSUtvWkl6ajBFQXdJd1JERUxNQWtHQTFVRUJoTUNVMFV4RFRBTEJnTlZCQW9NQkVSSlIwY3hKakFrQmdOVkJBTU1IVVJKUjBjZ1YyRnNiR1YwSUVWamIzTjVjM1JsYlNCU2IyOTBJRU5CTUI0WERUSTJNREl5TmpFME5UQXpNRm9YRFRJNE1EVXpNVEUwTlRBek1Gb3dQVEVMTUFrR0ExVUVCaE1DVTBVeERUQUxCZ05WQkFvTUJFUkpSMGN4SHpBZEJnTlZCQU1NRmxCSlJDQkpjM04xWlhJZ0tFVmpiM041YzNSbGJTa3dXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUXJiRjRnOWluWVFEOGFGQ0FsZFc3Y296d3BUTkY0blpoMWoyMStIVE5GWWQ3REZZb1IyRkpnTlZmQ1JOa1ZwZkFKUFVSQjNwSGdaUzhmbUhjYVZ4T1ZvMlF3WWpBZ0JnTlZIUkVFR1RBWGdnbHNiMk5oYkdodmMzU0NDbkJwWkMxcGMzTjFaWEl3SFFZRFZSME9CQllFRk1nNmdENm1QMG84WjVBbld4Vk1UbFdXa3dPU01COEdBMVVkSXdRWU1CYUFGQ0RrS2IwbHYyclY0bVhRMDUxaEg4OHdIS2Y3TUFvR0NDcUdTTTQ5QkFNQ0EwY0FNRVFDSUErS085WnREbndBdjlpV2txdFFnaDRRb3FLbzI0Qmo0N0E1aThpU1hKSi9BaUE0aE5paklmUSs1Q0pZQXJVbkIrNjJycGNrVWxFK1RtN1JMa25NdFA4a1ZRPT0iLCJNSUlCM0RDQ0FZT2dBd0lCQWdJVWVFN0c3WGNkYkZ6cForUVJNeUVpRjhGSEplNHdDZ1lJS29aSXpqMEVBd0l3UkRFTE1Ba0dBMVVFQmhNQ1UwVXhEVEFMQmdOVkJBb01CRVJKUjBjeEpqQWtCZ05WQkFNTUhVUkpSMGNnVjJGc2JHVjBJRVZqYjNONWMzUmxiU0JTYjI5MElFTkJNQjRYRFRJMk1ESXlOakUwTlRBek1Gb1hEVE0yTURJeU5ERTBOVEF6TUZvd1JERUxNQWtHQTFVRUJoTUNVMFV4RFRBTEJnTlZCQW9NQkVSSlIwY3hKakFrQmdOVkJBTU1IVVJKUjBjZ1YyRnNiR1YwSUVWamIzTjVjM1JsYlNCU2IyOTBJRU5CTUZrd0V3WUhLb1pJemowQ0FRWUlLb1pJemowREFRY0RRZ0FFMlRLRmJoMkU1TVZ2RVpzSDlqWUV0VURTZWJaeEc2UHNmWlhsUm1rajFXaEo5dEY1RG9ZT21Oazl4RFRxZExQRlMxMzVjdURlU1k4VEwya3NpaXhUUDZOVE1GRXdIUVlEVlIwT0JCWUVGQ0RrS2IwbHYyclY0bVhRMDUxaEg4OHdIS2Y3TUI4R0ExVWRJd1FZTUJhQUZDRGtLYjBsdjJyVjRtWFEwNTFoSDg4d0hLZjdNQThHQTFVZEV3RUIvd1FGTUFNQkFmOHdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdKUEx5aTkrMUVMUU5Td2tVVnBXekIxT2F4L20wUkNmSGdOVm4wd0ZlNTF3Q0lFOXUyOUF5UEVETnhTVGhDK2dJTG0wQ2NBaThWUUJUQ084aEY4dFBlWHp2Il0sImtpZCI6InBpZF9pc3N1ZXIiLCJ0eXAiOiJkYytzZC1qd3QiLCJhbGciOiJFUzI1NiJ9.eyJfc2QiOlsibzJ5SFplLVJZNWVXMTR4Q2lWXzRhY1JHZWZrQ283VkxqSUdYMDBSUmU5RSIsIlNUc3ZtSldmZWRMR2RGUGFpLXpHY0tYMlA5OXU5SFhhRlo2WHJ6V3o5bVEiLCJ0Q1I0d2xkQmc1ZktmQ3BUYjhBeDc3TWEzdEt3MVgwR2dJTWc4MVBDd1RBIiwiUDh1WlJ5QkdrLVB6T0c0THFVSDh4cUs5c3Ywcy1nSnVsejREcjFKWDJRMCIsIkJGYkJsalRnRkhDdmVyVVN2a3VXN0h0eFo4c0ZmOTdKS281NVROOGNLSzgiLCJNR05QWDJRRVhsQ1g2dHotY1MtWVVyRkdzY084NXBLQzlEV1RoOG94VGJnIiwiLUZ0MGpFSDFEd1BvaEZBV19TQ0ZMb0E4MzFNeFhMd1dJdktHcjJqbjVRZyIsImozTWQ3VW9ldWhOb0FBa3JXeGFzeEd6Z01tNWlQclpvTnhJbWYta0c4THciLCJQbVlFRTdvZHptTkFBbTg2TFlDMU5rdnpOQXg4MTdTNmdTUlZRR0FaZmdRIiwicVp0bUc4bm8wWlJ0WmhzU0ZlUEx5MVd2NURTMkx1Y3pLQWk3aFVGeVBwZyIsIlZSRW5XdFlzdUY0LWw2d3lhT2ZpRy02YWVYeDI0UkpwRTA1UlBvaGtldWsiLCIwNjJTU0xfY1VuWFpQMktPeUJEdEwwMUQydjdsRnItRDR1TFg0ZjM3MTNjIiwiMl9XZExtcXJVbFkzWFhjUUt6Wm9fb0NwbHp3Z2lNTWVVNmdkYmk1aktCdyIsIl9VU3JiZnVfZ3RwUEE1SnRpVGUxaEZVSUF1ZXRzeExmVE9lWjNmYUxkZkkiXSwibmJmIjoxNzcyMTE4NzQ4LCJ2Y3QiOiJ1cm46ZXVkaTpwaWQ6MSIsIl9zZF9hbGciOiJzaGEtMjU2IiwiaXNzIjoiaHR0cHM6Ly9sb2NhbGhvc3QvcGlkLWlzc3VlciIsImNuZiI6eyJqd2siOnsia3R5IjoiRUMiLCJ1c2UiOiJzaWciLCJjcnYiOiJQLTI1NiIsIngiOiJCMXBmQjB6djlmcEQxdnlWYURBUDhwR2F0T2pNOVhPVXgwM2pwMHd3N0JzIiwieSI6IkFzMUIxendObFB4N0MycVB4OGVPZ2hTblZxcmxpUmwzc3dlSGtaUmpyU2ciLCJhbGciOiJFUzI1NiJ9fSwiZXhwIjoxNzc0NzEwNzI4LCJpYXQiOjE3NzIxMTg3Mjh9.kWB8CaGhFSISwZkYypSiTuiWlUxvJ5BfONoVhHYdxnj9beYoP-GmB1DhGHyrQd8eWsMHdikNFk6JD_qQauEnew~WyJRMkNkbTZ3ODh4NmJIRFpIU0dfTFN3IiwiZmFtaWx5X25hbWUiLCJOZWFsIl0~WyJLWm9GZFVic2dMUEdwTXRxUU9hSldnIiwiZ2l2ZW5fbmFtZSIsIlR5bGVyIl0~WyJDY0lMX08zR1BzRmNQTzBfbGFCQkRRIiwiYmlydGhkYXRlIiwiMTk1NS0wNC0xMiJd~WyJ5US1sODNERU5oOFkwSXQ0Z1JqQ2FBIiwibG9jYWxpdHkiLCIxMDEgVHJhdW5lciJd~WyJwc3lySERiS3NkenZtQUtqeUdjTFNnIiwicGxhY2Vfb2ZfYmlydGgiLHsiX3NkIjpbIjJZMVB4bk5iQWtjTzJwWThCSlhNVXBEelpSNTNpYkFKNDhLc0NlbmxDZlUiXX1d~WyJPLTdKVDl1NGdRU1d6aGpDMkR6V1dRIiwiQVQiXQ~WyJfTjdvYzJwdnJDQXdFdy05bFB2d2N3IiwibmF0aW9uYWxpdGllcyIsW3siLi4uIjoiMEZwSFJPczFudG1MT3REZXBzM0dYU2ZGVWl4MTQ2SjZ6VmNkTWp2bzNJbyJ9XV0~WyJaWTJtc2RXSDBrWmhzZVZQZDlMOEtBIiwic3RyZWV0X2FkZHJlc3MiLCJUcmF1bmVyIl0~WyI1eXpGeERXUzhPb0ZJWEFURlhmRTZnIiwibG9jYWxpdHkiLCJHZW1laW5kZSBCaWJlcmJhY2giXQ~WyJuRzZfR3dETWJQNVRSM1FXX2FyQ0V3IiwicG9zdGFsX2NvZGUiLCIzMzMxIl0~WyI3b2s3em9KZUxUeGZhREowOGJjMWt3IiwiY291bnRyeSIsIkFUIl0~WyJOelNJdWtrUTVDbmJjc0hGaUk5WWdBIiwiYWRkcmVzcyIseyJfc2QiOlsiUVZRWDFSUlNQWjlSZUhmU0o3bk5FS2JJOVduV2JXWU94OVBmblRtR0tfYyIsIjE0SWN1ZTZmSWZ0Vm5zMU9GOEJLWktTOU9wNXlpT3JTWGhYVVZOUVY2X00iLCJhWXdwRDNXYlJtNnVBNXloc1JjQ3oxdFh6Y3JDQXZTTWxGSC1kdXo5RW5zIiwiWkxwWWNQT2F1UUtlSDBBaDMwTzNudmFJRmQxWnpQN0tkSlpXSWJFNFpxYyJdfV0~WyJyMjU1QlRDbXQ5ZlVyWG9HM1ZXeEx3IiwicGVyc29uYWxfYWRtaW5pc3RyYXRpdmVfbnVtYmVyIiwiMWI0ZDdhNGUtMjAxZC00NTVhLWE5YTQtYTU5MTZhMDkzNzIxIl0~WyJ6V1lGaF9PYnd1M09xcnhmS1VHXzdBIiwiZW1haWwiLCJ0eWxlci5uZWFsQGV4YW1wbGUuY29tIl0~WyI1ZXdCa0VCdU9WZDhoRlExTUpwMGJBIiwiZGF0ZV9vZl9leHBpcnkiLCIyMDI2LTA2LTA2Il0~WyJvY2JlRDlhVXNOOURkY3d5cUd6MGFBIiwiaXNzdWluZ19hdXRob3JpdHkiLCJTRSBBZG1pbmlzdHJhdGl2ZSBhdXRob3JpdHkiXQ~WyJzaFNIeV9xX0JBVnpyUFp2bWVPQkNnIiwiaXNzdWluZ19jb3VudHJ5IiwiU0UiXQ~WyJuV0s4QmpRZW00ZmxLNnJVaW5XUmFBIiwiZG9jdW1lbnRfbnVtYmVyIiwiOGZkNmMwYzEtNTY5OC00N2Q3LTg5NzYtMDk4ZjVlMTg0ZWYzIl0~WyJGUkRucmtrcTE1TllSWjU5MGxsWTlRIiwiaXNzdWluZ19qdXJpc2RpY3Rpb24iLCJTRS1ZIl0~WyJud1hUV0F6UGlXbDNMSWplS2tOZVZ3IiwiZGF0ZV9vZl9pc3N1YW5jZSIsIjIwMjYtMDItMjYiXQ~";

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
