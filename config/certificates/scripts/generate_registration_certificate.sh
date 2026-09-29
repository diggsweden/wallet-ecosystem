#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# Generates the local WRPRC registration certificate as a signed JWT, not an X.509 certificate.

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
CERT_DIR="$SCRIPT_DIR/.."
KEYSTORE="$CERT_DIR/verifier-registration-certificate/verifier-registration-certificate.p12"
OUTPUT_FILE="$CERT_DIR/verifier-registration-certificate/registration_certificate.jwt"
ENV_FILE="$CERT_DIR/verifier-registration-certificate/registration_certificate.env"

: "${VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD:?Environment variable VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD is not set}"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

create_license() {
  local target_file="$1"
  cat >"${target_file}.license" <<EOF
# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0
EOF
}

base64url_encode() {
  base64 -w0 | tr '+/' '-_' | tr -d '='
}

certificate_base64() {
  local selector="$1"
  openssl pkcs12 \
    -in "$KEYSTORE" \
    -passin "pass:$VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD" \
    -nokeys "$selector" 2>/dev/null |
    openssl x509 -outform DER 2>/dev/null |
    base64 -w0
}

der_signature_to_raw() {
  local signature_file="$1"
  local parsed r_hex s_hex

  parsed=$(openssl asn1parse -inform DER -in "$signature_file" 2>/dev/null)
  r_hex=$(echo "$parsed" | awk 'NR==2 { sub(/.*:/, ""); print }')
  s_hex=$(echo "$parsed" | awk 'NR==3 { sub(/.*:/, ""); print }')

  while [[ ${#r_hex} -gt 64 && "${r_hex:0:2}" == "00" ]]; do r_hex="${r_hex:2}"; done
  while [[ ${#s_hex} -gt 64 && "${s_hex:0:2}" == "00" ]]; do s_hex="${s_hex:2}"; done
  while [[ ${#r_hex} -lt 64 ]]; do r_hex="0${r_hex}"; done
  while [[ ${#s_hex} -lt 64 ]]; do s_hex="0${s_hex}"; done

  echo -n "${r_hex}${s_hex}" | xxd -r -p
}

now=$(date -u +%s)
expires=$((now + 31536000))
leaf_certificate=$(certificate_base64 -clcerts)
ca_certificate=$(certificate_base64 -cacerts)

header=$(printf '{"typ":"rc-wrp+jwt","alg":"ES256","x5c":["%s","%s"]}' \
  "$leaf_certificate" "$ca_certificate")
payload=$(
  cat <<EOF
{
  "name": "DIGG Wallet Ecosystem",
  "sub": "VATSE-12345678",
  "country": "SE",
  "iat": ${now},
  "exp": ${expires},
  "registry_uri": "https://localhost/local-registration-certificate/registry",
  "srv_description": [
    {
      "lang": "sv",
      "value": "Lokal wallet-ekosystemtjänst"
    },
    {
      "lang": "en",
      "value": "Local wallet ecosystem service"
    }
  ],
  "entitlements": [
    "https://uri.etsi.org/19475/Entitlement/Service_Provider"
  ],
  "purpose": [
    {
      "lang": "sv",
      "value": "Identifiering med intyg"
    },
    {
      "lang": "en",
      "value": "Identification with credentials"
    }
  ],
  "credentials": [
    {
      "format": "dc+sd-jwt",
      "meta": {
        "vct_values": ["urn:eudi:pid:1"]
      },
      "claim": [
        {"path": ["personal_administrative_number"]},
        {"path": ["family_name"]},
        {"path": ["given_name"]},
        {"path": ["birthdate"]},
        {"path": ["address", "street_address"]},
        {"path": ["address", "postal_code"]},
        {"path": ["address", "locality"]},
        {"path": ["address", "country"]}
      ]
    }
  ],
  "service_identifier": "wallet-ecosystem",
  "intended_use_id": "1",
  "privacy_policy": "https://localhost/local-registration-certificate/privacy",
  "info_uri": "https://localhost/local-registration-certificate/info",
  "support_uri": "https://localhost/local-registration-certificate/support",
  "supervisory_authority": {
    "name": "Local Wallet Ecosystem",
    "country": "SE",
    "email": "walletecosystem@example.com"
  },
  "policy_id": ["0.4.0.19475.3.1"],
  "certificate_policy": "https://localhost/local-registration-certificate/certificate-policy",
  "status": {
    "status_list": {
      "uri": "https://localhost/local-registration-certificate/registration-certificate-status",
      "idx": 0
    }
  }
}
EOF
)

header_base64=$(printf '%s' "$header" | base64url_encode)
payload_base64=$(printf '%s' "$payload" | base64url_encode)
signing_input="${header_base64}.${payload_base64}"

openssl pkcs12 \
  -in "$KEYSTORE" \
  -passin "pass:$VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD" \
  -nocerts -nodes -out "$TMP_DIR/signing-key.pem" 2>/dev/null
printf '%s' "$signing_input" |
  openssl dgst -sha256 -sign "$TMP_DIR/signing-key.pem" -out "$TMP_DIR/signature.der"
signature=$(der_signature_to_raw "$TMP_DIR/signature.der" | base64url_encode)

mkdir -p "$(dirname "$OUTPUT_FILE")"
registration_certificate="${signing_input}.${signature}"
printf '%s' "$registration_certificate" >"$OUTPUT_FILE"
printf 'VERIFIER_INTENDEDUSES_0_REGISTRATIONCERTIFICATE=%s\n' \
  "$registration_certificate" >"$ENV_FILE"

create_license "$OUTPUT_FILE"
create_license "$ENV_FILE"
echo "Successfully wrote $OUTPUT_FILE"
