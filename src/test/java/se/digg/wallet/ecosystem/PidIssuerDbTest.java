// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

public class PidIssuerDbTest {

  @Test
  void isReachableAndHasIssuedCredentialTable() throws SQLException {
    assumeTrue(Boolean.parseBoolean(Property.PID_ISSUER_DB_TESTING_ENABLED.getValue()));

    try (Connection conn = DriverManager.getConnection(
        ServiceIdentifier.PID_ISSUER_DB.toString(), "pid-issuer", "pass")) {
      assertThat(conn.isValid(5), is(true));

      DatabaseMetaData metaData = conn.getMetaData();
      try (ResultSet tables =
          metaData.getTables(null, null, "issued_credential", new String[] {"TABLE"})) {
        if (!tables.next()) {
          org.junit.jupiter.api.Assertions
              .fail("Table 'issued_credential' should exist in pid-issuer-db");
        }
      }
    }
  }
}
