// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

public enum IntendedUse {
  PID("1"),
  LEARNING_CREDENTIAL("2");

  private final String id;

  IntendedUse(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
