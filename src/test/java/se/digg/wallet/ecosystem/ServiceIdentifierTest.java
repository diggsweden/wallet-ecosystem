// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static se.digg.wallet.ecosystem.ServiceIdentifier.KEYCLOAK;
import static se.digg.wallet.ecosystem.ServiceIdentifier.PID_ISSUER;
import static se.digg.wallet.ecosystem.ServiceIdentifier.UNTRUSTED_KEYCLOAK;
import static se.digg.wallet.ecosystem.ServiceIdentifier.UNTRUSTED_PID_ISSUER;
import static se.digg.wallet.ecosystem.ServiceIdentifier.UNTRUSTED_WALLET_PROVIDER;
import static se.digg.wallet.ecosystem.ServiceIdentifier.VERIFIER_BACKEND;
import static se.digg.wallet.ecosystem.ServiceIdentifier.VERIFIER_FRONTEND;
import static se.digg.wallet.ecosystem.ServiceIdentifier.WALLET_CLIENT_GATEWAY;
import static se.digg.wallet.ecosystem.ServiceIdentifier.WALLET_PROVIDER;
import static se.digg.wallet.ecosystem.ServiceIdentifier.values;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.Standard.class)
class ServiceIdentifierTest {

  @Test
  void hasSpecificEnvironmentVariableNames() {
    assertThat(Stream.of(values()).collect(
        Collectors.toMap(Function.identity(), ServiceIdentifier::getEnvironmentVariableName)),
        equalTo(Map.of(
            KEYCLOAK, "DIGG_WALLET_ECOSYSTEM_KEYCLOAK_BASE_URI",
            UNTRUSTED_KEYCLOAK, "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_KEYCLOAK_BASE_URI",
            PID_ISSUER, "DIGG_WALLET_ECOSYSTEM_PID_ISSUER_BASE_URI",
            UNTRUSTED_PID_ISSUER, "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_PID_ISSUER_BASE_URI",
            WALLET_PROVIDER, "DIGG_WALLET_ECOSYSTEM_WALLET_PROVIDER_BASE_URI",
            UNTRUSTED_WALLET_PROVIDER, "DIGG_WALLET_ECOSYSTEM_UNTRUSTED_WALLET_PROVIDER_BASE_URI",
            VERIFIER_BACKEND, "DIGG_WALLET_ECOSYSTEM_VERIFIER_BACKEND_BASE_URI",
            VERIFIER_FRONTEND, "DIGG_WALLET_ECOSYSTEM_VERIFIER_FRONTEND_BASE_URI",
            WALLET_CLIENT_GATEWAY, "DIGG_WALLET_ECOSYSTEM_WALLET_CLIENT_GATEWAY_BASE_URI")));
  }
}
