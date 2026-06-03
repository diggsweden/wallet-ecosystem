// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.jwk.ECKey;

public interface IssuanceAgent {

  String issuePidCredential(ECKey bindingKey, String username, String password) throws Exception;
}
