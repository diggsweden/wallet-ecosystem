// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ServiceIdentifierTest {

  @ParameterizedTest
  @EnumSource(ServiceIdentifier.class)
  void isValidURI(ServiceIdentifier s) {
    assertThat(s.toUri(), is(notNullValue()));
  }
}
