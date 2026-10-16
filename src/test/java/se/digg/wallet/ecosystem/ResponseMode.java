// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

public enum ResponseMode {
  DIRECT_POST("direct_post") {
    @Override
    public Map<String, String> getFormParams(
        SignedJWT signedAuthRequest, Map<String, List<String>> vpToken) throws ParseException {
      return Map.of(
          "state", signedAuthRequest.getJWTClaimsSet().getStringClaim("state"),
          "vp_token", JSON_WRITER.writeValueAsString(vpToken));
    }
  },

  DIRECT_POST_JWT("direct_post.jwt") {
    @Override
    public Map<String, String> getFormParams(
        SignedJWT signedAuthRequest, Map<String, List<String>> vpToken)
        throws ParseException, JOSEException {
      return Map.of("response", DirectPostJwtPayload.create(signedAuthRequest, vpToken));
    }
  };

  private static final ObjectWriter JSON_WRITER = JsonMapper.shared().writer();

  private final String value;

  ResponseMode(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public abstract Map<String, String> getFormParams(
      SignedJWT signedAuthRequest, Map<String, List<String>> vpToken) throws Exception;

  @Override
  public String toString() {
    return value;
  }
}
