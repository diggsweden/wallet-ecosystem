#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

set -euo pipefail

# Base directories
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
CERT_DIR="$SCRIPT_DIR/.."
TMP_DIR="$CERT_DIR/tmp"

# Configuration (Must be provided by wrapper script)
: "${CRL_DP:?Environment variable CRL_DP is not set}"
: "${STATUS_LIST_URL:?Environment variable STATUS_LIST_URL is not set}"
: "${AIA_URL:?Environment variable AIA_URL is not set}"
: "${PID_ISSUER_OUT:?Environment variable PID_ISSUER_OUT is not set}"
: "${TRUST_SOURCE_OUT:?Environment variable TRUST_SOURCE_OUT is not set}"
: "${VERIFIER_SANS:?Environment variable VERIFIER_SANS is not set}"
: "${PROVIDER_SANS:?Environment variable PROVIDER_SANS is not set}"
: "${ISSUER_SANS:?Environment variable ISSUER_SANS is not set}"
: "${TRUST_SOURCE_SANS:?Environment variable TRUST_SOURCE_SANS is not set}"

export crl_dp="$CRL_DP"
export status_list_url="$STATUS_LIST_URL"
export aia_url="$AIA_URL"

# Cleanup temporary files on exit
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR"

# Helper to create REUSE license files
function create_license() {
  local target_file="$1"
  cat >"${target_file}.license" <<EOF
# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0
EOF
}

# 1. Root CA configuration
ROOTCA_DIR="$CERT_DIR/rootca"
mkdir -p "$ROOTCA_DIR"
ROOT_KEY="$ROOTCA_DIR/rootca_private_key.pem"
ROOT_PEM="$ROOTCA_DIR/rootca.pem"

if [ ! -f "$ROOT_KEY" ] || [ ! -f "$ROOT_PEM" ]; then
  echo "Generating EC Root CA (P-256)..."
  openssl ecparam -name prime256v1 -genkey -noout -out "$ROOT_KEY"
  cp "$SCRIPT_DIR/templates/root.cnf" "$TMP_DIR/root.cnf"
  openssl req -x509 -new -nodes -key "$ROOT_KEY" \
    -sha256 -days 3650 \
    -out "$ROOT_PEM" \
    -config "$TMP_DIR/root.cnf"
  create_license "$ROOT_KEY"
  create_license "$ROOT_PEM"
else
  echo "Using existing Root CA found in $ROOTCA_DIR"
fi

if [ ! -f "$ROOTCA_DIR/revocation-list.pem" ]; then
  echo "Generating empty Certificate Revocation List (CRL)..."
  export TMP_DIR ROOT_PEM ROOT_KEY
  envsubst <"$SCRIPT_DIR/templates/ca.cnf" >"$TMP_DIR/ca.cnf"
  touch "$TMP_DIR/index.txt"
  echo "01" >"$TMP_DIR/crlnumber"
  openssl ca -gencrl -config "$TMP_DIR/ca.cnf" -out "$ROOTCA_DIR/revocation-list.pem"
  create_license "$ROOTCA_DIR/revocation-list.pem"
fi

function generate_service_cert_ec() {
  local target_subdir="$1"
  local cert_name="$2"
  local alias_name="$3"
  local password="$4"
  local cn="$5"
  local sans="$6"
  local template_name="${7:-service.cnf}"

  local target_dir="$CERT_DIR/$target_subdir"
  mkdir -p "$target_dir"

  echo "Processing $cert_name (EC) for $target_subdir..."

  # Create temporary config for CSR
  local cnf_file="$TMP_DIR/$cert_name.cnf"
  export cn sans
  envsubst <"$SCRIPT_DIR/templates/$template_name" >"$cnf_file"

  local key_file="$TMP_DIR/$cert_name.key"
  local csr_file="$TMP_DIR/$cert_name.csr"
  local crt_file="$TMP_DIR/$cert_name.crt"
  local p12_file="$target_dir/$cert_name.p12"

  # Generate EC Private Key (P-256)
  openssl ecparam -name prime256v1 -genkey -noout -out "$key_file"

  # Generate CSR
  openssl req -new -key "$key_file" -out "$csr_file" -config "$cnf_file"

  # Sign Cert
  openssl x509 -req -in "$csr_file" \
    -CA "$ROOT_PEM" -CAkey "$ROOT_KEY" -CAcreateserial \
    -out "$crt_file" -days 825 -sha256 \
    -extfile "$cnf_file" -extensions v3_req

  # Create P12
  rm -f "$p12_file"
  openssl pkcs12 -export \
    -in "$crt_file" -inkey "$key_file" -certfile "$ROOT_PEM" \
    -out "$p12_file" \
    -name "$alias_name" \
    -passout "pass:$password"

  create_license "$p12_file"

  # Save cert for trust store use by other services
  cp "$crt_file" "$TMP_DIR/$cert_name.crt.trust"
}

# --- Service Certificates ---

# 1. PID Issuer
generate_service_cert_ec "issuer" "pid_issuer" "pid_issuer" "pass1234" "PID Issuer (Ecosystem)" "$ISSUER_SANS"
cp "$TMP_DIR/pid_issuer.crt" "$CERT_DIR/issuer/pid_issuer_cert.pem"

# Add nonce-encryption and request-encryption keys to pid_issuer.p12
generate_service_cert_ec "issuer" "nonce" "nonce-encryption" "pass1234" "nonce-encryption" "DNS.1:localhost" "encryption.cnf"
generate_service_cert_ec "issuer" "request" "request-encryption" "pass1234" "request-encryption" "DNS.1:localhost" "encryption.cnf"

keytool -importkeystore -srckeystore "$CERT_DIR/issuer/nonce.p12" -srcstoretype PKCS12 -srcstorepass pass1234 -destkeystore "$CERT_DIR/issuer/pid_issuer.p12" -deststoretype PKCS12 -deststorepass pass1234 -noprompt
keytool -importkeystore -srckeystore "$CERT_DIR/issuer/request.p12" -srcstoretype PKCS12 -srcstorepass pass1234 -destkeystore "$CERT_DIR/issuer/pid_issuer.p12" -deststoretype PKCS12 -deststorepass pass1234 -noprompt
rm -f "$CERT_DIR/issuer/nonce.p12" "$CERT_DIR/issuer/request.p12" "$CERT_DIR/issuer/nonce.p12.license" "$CERT_DIR/issuer/request.p12.license"

echo "Generating issuer_wrprc.jwt for PID Issuer (using bash)..."

# Define target paths
JWT_PATH="${PID_ISSUER_OUT}/issuer_wrprc.jwt"
PRE_JWT_PATH="${PID_ISSUER_OUT}/issuer_wrprc.json"

# Extract cert body, removing headers/footers and newlines
CERT_B64=$(grep -v -- '---' "${TMP_DIR}/pid_issuer.crt" | tr -d '\n')

# Create JSON payload and header
read -r -d '' HEADER_JSON <<EOF || true
{"typ":"rc-wrp+jwt","alg":"ES256","x5c":["${CERT_B64}"]}
EOF

read -r -d '' PAYLOAD_JSON <<EOF || true
{"iss":"did:web:localhost","sub":"did:web:localhost"}
EOF

# Base64url encode
B64_HEADER=$(echo -n "$HEADER_JSON" | base64 -w0 | tr '+/' '-_' | tr -d '=')
B64_PAYLOAD=$(echo -n "$PAYLOAD_JSON" | base64 -w0 | tr '+/' '-_' | tr -d '=')
SIGNING_INPUT="${B64_HEADER}.${B64_PAYLOAD}"

# Sign with openssl
echo -n "$SIGNING_INPUT" | openssl dgst -sha256 -sign "${TMP_DIR}/pid_issuer.key" -out "${TMP_DIR}/sig.der"

# Convert DER signature to Raw R||S
PARSED=$(openssl asn1parse -inform DER -in "${TMP_DIR}/sig.der" 2>/dev/null)
R_HEX=$(echo "$PARSED" | awk 'NR==2 { sub(/.*:/, ""); print }')
S_HEX=$(echo "$PARSED" | awk 'NR==3 { sub(/.*:/, ""); print }')

# Strip leading padding
while [[ ${#R_HEX} -gt 64 && "${R_HEX:0:2}" == "00" ]]; do R_HEX="${R_HEX:2}"; done
while [[ ${#S_HEX} -gt 64 && "${S_HEX:0:2}" == "00" ]]; do S_HEX="${S_HEX:2}"; done
# Left-pad to exactly 32 bytes (64 hex chars)
while [[ ${#R_HEX} -lt 64 ]]; do R_HEX="0${R_HEX}"; done
while [[ ${#S_HEX} -lt 64 ]]; do S_HEX="0${S_HEX}"; done

SIG_B64=$(echo -n "${R_HEX}${S_HEX}" | xxd -r -p | base64 -w0 | tr '+/' '-_' | tr -d '=')

# Write outputs
echo -n "${SIGNING_INPUT}.${SIG_B64}" >"$JWT_PATH"
cat >"$PRE_JWT_PATH" <<EOF
{
  "headers": $HEADER_JSON,
  "payload": $PAYLOAD_JSON
}
EOF
jq . "$PRE_JWT_PATH" >"${PRE_JWT_PATH}.tmp" && mv "${PRE_JWT_PATH}.tmp" "$PRE_JWT_PATH"

create_license "$JWT_PATH"
create_license "$PRE_JWT_PATH"

# 2. Verifier Backend
generate_service_cert_ec "verifier" "verifier_backend" "verifier_backend" "pass1234" "Verifier Backend (Ecosystem)" "$VERIFIER_SANS"

# 3. Verifier Trust Store
echo "Creating trusted_issuers.p12 for Verifier..."
TRUST_P12="$CERT_DIR/trust-validator/trusted_issuers.p12"
mkdir -p "$CERT_DIR/trust-validator"
rm -f "$TRUST_P12"
keytool -importcert -noprompt -alias pid_issuer -file "$TMP_DIR/pid_issuer.crt.trust" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
keytool -importcert -noprompt -alias root_ca -file "$ROOT_PEM" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
create_license "$TRUST_P12"

# 4. Wallet Provider
generate_service_cert_ec "wallet-provider" "wallet_provider" "wallet_provider" "pass1234" "Wallet Provider (Ecosystem)" "$PROVIDER_SANS"

# 5. Trust Source
generate_service_cert_ec "trust-list-signer" "trust_source" "trust_source" "pass1234" "Trust Source (Ecosystem)" "$TRUST_SOURCE_SANS" "signer.cnf"
cp "$TMP_DIR/trust_source.crt" "$CERT_DIR/trust-list-signer/trust_source_cert.pem"
cp "$TMP_DIR/trust_source.key" "$CERT_DIR/trust-list-signer/trust_source_key.pem"

echo "Generating status-list.jwt for Trust Source..."
# Extract cert body, removing headers/footers and newlines
STATUS_CERT_B64=$(grep -v -- '---' "${CERT_DIR}/trust-list-signer/trust_source_cert.pem" | tr -d '\n')

read -r -d '' STATUS_HEADER_JSON <<EOF || true
{"typ":"statuslist+jwt","alg":"ES256","x5c":["${STATUS_CERT_B64}"]}
EOF

STATUS_PAYLOAD='{"iss":"'"${status_list_url}"'","sub":"'"${status_list_url}"'","iat":1700000000,"exp":1893456000,"status_list":{"bits":1,"lst":"eJxjYBgFo2AUjFQAAAQAAAE"}}'

STATUS_B64_HEADER=$(echo -n "$STATUS_HEADER_JSON" | base64 -w0 | tr '+/' '-_' | tr -d '=')
STATUS_B64_PAYLOAD=$(echo -n "$STATUS_PAYLOAD" | base64 -w0 | tr '+/' '-_' | tr -d '=')
STATUS_SIGNING_INPUT="${STATUS_B64_HEADER}.${STATUS_B64_PAYLOAD}"

echo -n "$STATUS_SIGNING_INPUT" | openssl dgst -sha256 -sign "${CERT_DIR}/trust-list-signer/trust_source_key.pem" -out "${TMP_DIR}/status_sig.der"

STATUS_PARSED=$(openssl asn1parse -inform DER -in "${TMP_DIR}/status_sig.der" 2>/dev/null)
STATUS_R_HEX=$(echo "$STATUS_PARSED" | awk 'NR==2 { sub(/.*:/, ""); print }')
STATUS_S_HEX=$(echo "$STATUS_PARSED" | awk 'NR==3 { sub(/.*:/, ""); print }')

while [[ ${#STATUS_R_HEX} -gt 64 && "${STATUS_R_HEX:0:2}" == "00" ]]; do STATUS_R_HEX="${STATUS_R_HEX:2}"; done
while [[ ${#STATUS_S_HEX} -gt 64 && "${STATUS_S_HEX:0:2}" == "00" ]]; do STATUS_S_HEX="${STATUS_S_HEX:2}"; done
while [[ ${#STATUS_R_HEX} -lt 64 ]]; do STATUS_R_HEX="0${STATUS_R_HEX}"; done
while [[ ${#STATUS_S_HEX} -lt 64 ]]; do STATUS_S_HEX="0${STATUS_S_HEX}"; done

STATUS_SIG_B64=$(echo -n "${STATUS_R_HEX}${STATUS_S_HEX}" | xxd -r -p | base64 -w0 | tr '+/' '-_' | tr -d '=')

mkdir -p "${TRUST_SOURCE_OUT}/signed"
echo -n "${STATUS_SIGNING_INPUT}.${STATUS_SIG_B64}" >"${TRUST_SOURCE_OUT}/signed/status-list.jwt"
cat >"${TRUST_SOURCE_OUT}/signed/status-list.json" <<EOF
{
  "headers": $STATUS_HEADER_JSON,
  "payload": $STATUS_PAYLOAD
}
EOF
jq . "${TRUST_SOURCE_OUT}/signed/status-list.json" >"${TRUST_SOURCE_OUT}/signed/status-list.json.tmp" && mv "${TRUST_SOURCE_OUT}/signed/status-list.json.tmp" "${TRUST_SOURCE_OUT}/signed/status-list.json"

create_license "${TRUST_SOURCE_OUT}/signed/status-list.jwt"
create_license "${TRUST_SOURCE_OUT}/signed/status-list.json"

# 6. Trust Validator
echo "Creating trust_store.p12 for Trust Validator..."
TRUST_VALIDATOR_TRUST_STORE="$CERT_DIR/trust-validator/trust_store.p12"
mkdir -p "$CERT_DIR/trust-validator"
rm -f "$TRUST_VALIDATOR_TRUST_STORE"
keytool -importcert -noprompt -alias trust_source -file "$TMP_DIR/trust_source.crt.trust" -keystore "$TRUST_VALIDATOR_TRUST_STORE" -storepass pass1234 -storetype PKCS12
keytool -importcert -noprompt -alias root_ca -file "$ROOT_PEM" -keystore "$TRUST_VALIDATOR_TRUST_STORE" -storepass pass1234 -storetype PKCS12
create_license "$TRUST_VALIDATOR_TRUST_STORE"

# --- Finalization ---

# License for serial file if it exists
[ -f "$ROOTCA_DIR/rootca.srl" ] && create_license "$ROOTCA_DIR/rootca.srl"

# Ensure container readability
find "$CERT_DIR" -name "*.p12" -exec chmod 644 {} +
find "$CERT_DIR" -name "*.pem" -exec chmod 644 {} +

# Copy CRL to trust-source to be hosted by Nginx
cp "$ROOTCA_DIR/revocation-list.pem" "${TRUST_SOURCE_OUT}/revocation-list.pem"

echo "Done! All certificates and licenses in config/certificates updated for Ecosystem."
