// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jwt.SignedJWT;
import java.util.Map;

public enum ResponseMode {
  DIRECT_POST("direct_post") {
    @Override
    public Map<String, String> getFormParams(
        SignedJWT signedAuthRequest, String state, String dcqlId, String vpToken) {
      return Map.of(
          "state", state,
          "vp_token", String.format("{ \"%s\": [ \"%s\" ] }", dcqlId, vpToken));
    }
  },

  DIRECT_POST_JWT("direct_post.jwt") {
    @Override
    public Map<String, String> getFormParams(
        SignedJWT signedAuthRequest, String state, String dcqlId, String vpToken)
        throws Exception {
      return Map.of(
          "response", DirectPostJwtPayload.create(signedAuthRequest, state, dcqlId, vpToken));
    }
  };

  private final String value;

  ResponseMode(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public abstract Map<String, String> getFormParams(
      SignedJWT signedAuthRequest, String state, String dcqlId, String vpToken) throws Exception;

  @Override
  public String toString() {
    return value;
  }
}
