// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record SdJwtVc(
    SignedJWT issuerJwt,
    Map<String, String> disclosedClaims) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public SdJwtVc {
    disclosedClaims = Map.copyOf(disclosedClaims);
  }

  public static SdJwtVc parse(String raw) {
    Objects.requireNonNull(raw, "raw SD-JWT string must not be null");
    String[] parts = raw.split("~");
    if (parts.length == 0 || parts[0].isBlank()) {
      throw new IllegalArgumentException("Invalid SD-JWT: missing issuer JWT header");
    }

    SignedJWT issuerJwt;
    try {
      issuerJwt = SignedJWT.parse(parts[0]);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Failed to parse issuer SignedJWT", e);
    }

    Map<String, String> claims =
        Arrays.stream(parts)
            .skip(1)
            .filter(part -> !part.isBlank() && !part.contains("."))
            .map(part -> new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8))
            .map(
                decoded -> {
                  try {
                    return OBJECT_MAPPER.readTree(decoded);
                  } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to parse SD-JWT disclosure", e);
                  }
                })
            .filter(node -> node.isArray() && node.size() == 3)
            .map(node -> List.of(node.get(1).asText(), node.get(2).asText()))
            .collect(Collectors.toMap(List::getFirst, List::getLast, (a, b) -> b));

    return new SdJwtVc(issuerJwt, claims);
  }

  public String getIssuer() {
    try {
      return issuerJwt.getJWTClaimsSet().getIssuer();
    } catch (ParseException e) {
      throw new IllegalStateException("Failed to parse issuer claims", e);
    }
  }

  public String getDisclosedClaim(String claimName) {
    return disclosedClaims.get(claimName);
  }
}
