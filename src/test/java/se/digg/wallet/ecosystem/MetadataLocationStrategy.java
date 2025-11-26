// SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
//
// SPDX-License-Identifier: EUPL-1.2

package se.digg.wallet.ecosystem;

import java.net.URI;
import java.net.URISyntaxException;

public enum MetadataLocationStrategy {

  /**
   * This strategy locates the metadata as a descendant under the service root, e.g.
   * <code>https://example.com/tenant/.well-known/metadata</code>
   */
  BASIC {
    @Override
    protected String getPath(String identifierPath, String metadataPath) {
      return identifierPath + metadataPath;
    }
  },

  /**
   * This strategy locates the metadata under a well-known path directly under the service host and
   * is compliant with the OpenID for Verifiable Credential Issuance specification, e.g.
   * <code>https://example.com/.well-known/metadata/tenant</code>
   *
   * @see "https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#section-12.2.2"
   */
  OID4VCI_COMPLIANT {
    @Override
    protected String getPath(String identifierPath, String metadataPath) {
      return metadataPath + identifierPath;
    }
  };

  public final URI applyTo(URI identifier, String metadata) {
    try {
      return new URI(
          identifier.getScheme(), identifier.getAuthority(),
          getPath(identifier.getPath(), "/.well-known/" + metadata),
          null, null);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract String getPath(String identifierPath, String metadataPath);
}
