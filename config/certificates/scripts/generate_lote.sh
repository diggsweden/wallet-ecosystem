#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# Generates a signed LoTE (List of Trusted Entities) JWT.
# Replaces generate_lote.py — no Python dependency required.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_FILE="$CERT_DIR/../trust-source/signed/wallet-providers.json"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# Base64url-encode stdin (RFC 7515 — no padding, URL-safe alphabet)
base64url_encode() {
  base64 -w0 | tr '+/' '-_' | tr -d '='
}

# Extract the client (leaf) certificate from a PKCS#12 as DER-encoded base64
get_clcert_base64() {
  local p12="$1" pass="$2"
  openssl pkcs12 -in "$p12" -passin "pass:$pass" -nokeys -clcerts 2>/dev/null \
    | openssl x509 -outform DER 2>/dev/null \
    | base64 -w0
}

# Extract the CA certificate from a PKCS#12 as DER-encoded base64
get_cacert_base64() {
  local p12="$1" pass="$2"
  openssl pkcs12 -in "$p12" -passin "pass:$pass" -nokeys -cacerts 2>/dev/null \
    | openssl x509 -outform DER 2>/dev/null \
    | base64 -w0
}

# Convert a PEM certificate to DER-encoded base64
get_pem_cert_base64() {
  openssl x509 -in "$1" -outform DER 2>/dev/null | base64 -w0
}

# Convert a DER-encoded ECDSA signature to the raw R||S format required by JWS.
# For ES256 (P-256) each integer is zero-padded/trimmed to exactly 32 bytes.
der_sig_to_raw() {
  local sig_file="$1"
  local parsed r_hex s_hex

  parsed=$(openssl asn1parse -inform DER -in "$sig_file" 2>/dev/null)

  # Lines 2 and 3 of asn1parse output contain the INTEGER values.
  # The hex value follows the last colon on each line.
  r_hex=$(echo "$parsed" | awk 'NR==2 { sub(/.*:/, ""); print }')
  s_hex=$(echo "$parsed" | awk 'NR==3 { sub(/.*:/, ""); print }')

  # Strip leading 00 sign-padding bytes
  while [[ ${#r_hex} -gt 64 && "${r_hex:0:2}" == "00" ]]; do r_hex="${r_hex:2}"; done
  while [[ ${#s_hex} -gt 64 && "${s_hex:0:2}" == "00" ]]; do s_hex="${s_hex:2}"; done

  # Left-pad to exactly 32 bytes (64 hex chars)
  while [[ ${#r_hex} -lt 64 ]]; do r_hex="0${r_hex}"; done
  while [[ ${#s_hex} -lt 64 ]]; do s_hex="0${s_hex}"; done

  echo -n "${r_hex}${s_hex}" | xxd -r -p
}

# Create a compact JWS (header.payload.signature) signed with ES256.
sign_jwt() {
  local header_json="$1" payload_json="$2" key_pem="$3"

  local header_b64 payload_b64 signing_input sig_b64
  header_b64=$(printf '%s' "$header_json" | base64url_encode)
  payload_b64=$(printf '%s' "$payload_json" | base64url_encode)
  signing_input="${header_b64}.${payload_b64}"

  # ECDSA-SHA256 sign
  printf '%s' "$signing_input" \
    | openssl dgst -sha256 -sign "$key_pem" -out "$TMP_DIR/sig.der" 2>/dev/null

  sig_b64=$(der_sig_to_raw "$TMP_DIR/sig.der" | base64url_encode)

  printf '%s.%s' "$signing_input" "$sig_b64"
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

main() {
  local now next_update
  now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  next_update=$(date -u -d "+365 days" +%Y-%m-%dT%H:%M:%SZ)

  echo "Extracting certificates..."

  local wallet_clcert wallet_cacert issuer_clcert issuer_cacert trust_source_cert
  wallet_clcert=$(get_clcert_base64 "$CERT_DIR/wallet-provider/wallet_provider.p12" "pass1234")
  wallet_cacert=$(get_cacert_base64 "$CERT_DIR/wallet-provider/wallet_provider.p12" "pass1234")
  issuer_clcert=$(get_clcert_base64 "$CERT_DIR/issuer/pid_issuer.p12" "pass1234")
  issuer_cacert=$(get_cacert_base64 "$CERT_DIR/issuer/pid_issuer.p12" "pass1234")
  trust_source_cert=$(get_pem_cert_base64 "$CERT_DIR/trust-list-signer/trust_source_cert.pem")

  echo "Building LoTE payload..."

  # JSON is safe to interpolate here — base64 values contain only [A-Za-z0-9+/=]
  local payload
  read -r -d '' payload <<EOF || true
{
  "LoTE": {
    "ListAndSchemeInformation": {
      "LoTEVersionIdentifier": 1,
      "LoTESequenceNumber": 1,
      "LoTEType": "http://uri.etsi.org/19602/LoTEType/wallet-providers",
      "SchemeOperatorName": [{"lang": "en", "value": "DIGG"}],
      "SchemeName": [{"lang": "en", "value": "Local LoTE"}],
      "SchemeTerritory": "SE",
      "ListIssueDateTime": "${now}",
      "NextUpdate": "${next_update}"
    },
    "TrustedEntitiesList": [
      {
        "TrustedEntityInformation": {
          "TEName": [{"lang": "en", "value": "Local Wallet Provider"}],
          "TEAddress": {
            "TEPostalAddress": [{"lang": "en", "StreetAddress": "Local", "Country": "SE"}],
            "TEElectronicAddress": [{"lang": "en", "uriValue": "http://localhost"}]
          },
          "TEInformationURI": [{"lang": "en", "uriValue": "http://localhost"}]
        },
        "TrustedEntityServices": [
          {
            "ServiceInformation": {
              "ServiceName": [{"lang": "en", "value": "Local Wallet Issuance"}],
              "ServiceDigitalIdentity": {
                "X509Certificates": [
                  {"val": "${wallet_clcert}"},
                  {"val": "${wallet_cacert}"}
                ]
              },
              "ServiceTypeIdentifier": "http://uri.etsi.org/19602/SvcType/WalletSolution/Issuance",
              "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
              "StatusStartingTime": "${now}"
            }
          }
        ]
      },
      {
        "TrustedEntityInformation": {
          "TEName": [{"lang": "en", "value": "Local PID Issuer"}],
          "TEAddress": {
            "TEPostalAddress": [{"lang": "en", "StreetAddress": "Local", "Country": "SE"}],
            "TEElectronicAddress": [{"lang": "en", "uriValue": "http://localhost"}]
          },
          "TEInformationURI": [{"lang": "en", "uriValue": "http://localhost"}]
        },
        "TrustedEntityServices": [
          {
            "ServiceInformation": {
              "ServiceName": [{"lang": "en", "value": "Local PID Issuance"}],
              "ServiceDigitalIdentity": {
                "X509Certificates": [
                  {"val": "${issuer_clcert}"},
                  {"val": "${issuer_cacert}"}
                ]
              },
              "ServiceTypeIdentifier": "http://uri.etsi.org/19602/SvcType/PID/Issuance",
              "ServiceStatus": "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
              "StatusStartingTime": "${now}"
            }
          }
        ]
      }
    ]
  }
}
EOF

  local header
  header=$(printf '{"alg":"ES256","x5c":["%s"]}' "$trust_source_cert")

  echo "Signing LoTE as JWT..."
  local token
  token=$(sign_jwt "$header" "$payload" "$CERT_DIR/trust-list-signer/trust_source_key.pem")

  mkdir -p "$(dirname "$OUTPUT_FILE")"
  printf '%s' "$token" > "$OUTPUT_FILE"
  echo "Successfully wrote LoTE to $OUTPUT_FILE"
}

main "$@"
