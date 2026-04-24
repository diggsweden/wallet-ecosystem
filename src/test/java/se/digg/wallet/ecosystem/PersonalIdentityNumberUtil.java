// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.util.concurrent.ThreadLocalRandom;

public class PersonalIdentityNumberUtil {

  public static String getRandomPersonalId() {
    return Long.toString(
        ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L));
  }

}
