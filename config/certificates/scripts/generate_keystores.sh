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
: "${STATUS_LIST_URL:?Environment variable STATUS_LIST_URL is not set}"
: "${PID_ISSUER_OUT:?Environment variable PID_ISSUER_OUT is not set}"
: "${TRUST_SOURCE_OUT:?Environment variable TRUST_SOURCE_OUT is not set}"
: "${VERIFIER_SANS:?Environment variable VERIFIER_SANS is not set}"
: "${PROVIDER_SANS:?Environment variable PROVIDER_SANS is not set}"
: "${ISSUER_SANS:?Environment variable ISSUER_SANS is not set}"
: "${TRUST_SOURCE_SANS:?Environment variable TRUST_SOURCE_SANS is not set}"
: "${PID_ISSUER_KEYSTORE_PASSWORD:?Environment variable PID_ISSUER_KEYSTORE_PASSWORD is not set}"
: "${VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD:?Environment variable VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD is not set}"
: "${WALLET_PROVIDER_KEYSTORE_PASSWORD:?Environment variable WALLET_PROVIDER_KEYSTORE_PASSWORD is not set}"
: "${TRUST_SOURCE_KEYSTORE_PASSWORD:?Environment variable TRUST_SOURCE_KEYSTORE_PASSWORD is not set}"
: "${TRUST_VALIDATOR_TRUSTED_ISSUERS_PASSWORD:?Environment variable TRUST_VALIDATOR_TRUSTED_ISSUERS_PASSWORD is not set}"
: "${TRUST_VALIDATOR_TRUST_STORE_PASSWORD:?Environment variable TRUST_VALIDATOR_TRUST_STORE_PASSWORD is not set}"

export status_list_url="$STATUS_LIST_URL"

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

# 1. Independent self-signed CAs for each application service
CA_DIR="$CERT_DIR/ca"
mkdir -p "$CA_DIR"

generate_ca() {
  local ca_name="$1"
  local common_name="$2"
  local ca_dir="$CA_DIR/$ca_name"
  local ca_tmp_dir="$TMP_DIR/$ca_name"
  local ca_key="$ca_dir/ca_private_key.pem"
  local ca_pem="$ca_dir/ca.pem"
  local crl="$ca_dir/revocation-list.pem"

  mkdir -p "$ca_dir" "$ca_tmp_dir"
  if [ ! -f "$ca_key" ] || [ ! -f "$ca_pem" ]; then
    echo "Generating $common_name (P-256)..."
    openssl ecparam -name prime256v1 -genkey -noout -out "$ca_key"
    export cn="$common_name"
    envsubst <"$SCRIPT_DIR/templates/root.cnf" >"$ca_tmp_dir/root.cnf"
    openssl req -x509 -new -nodes -key "$ca_key" \
      -sha256 -days 3650 -out "$ca_pem" -config "$ca_tmp_dir/root.cnf"
    create_license "$ca_key"
    create_license "$ca_pem"
  else
    echo "Using existing $common_name found in $ca_dir"
  fi

  if [ ! -f "$crl" ]; then
    echo "Generating empty CRL for $common_name..."
    TMP_DIR="$ca_tmp_dir" ROOT_PEM="$ca_pem" ROOT_KEY="$ca_key" \
      envsubst <"$SCRIPT_DIR/templates/ca.cnf" >"$ca_tmp_dir/ca.cnf"
    touch "$ca_tmp_dir/index.txt"
    echo "01" >"$ca_tmp_dir/crlnumber"
    openssl ca -gencrl -config "$ca_tmp_dir/ca.cnf" -out "$crl"
    create_license "$crl"
  fi
}

generate_ca "pid-issuer" "DIGG Wallet PID Issuer CA"
generate_ca "wallet-provider" "DIGG Wallet Provider CA"
generate_ca "trust-source" "DIGG Wallet Trust Source CA"
generate_ca "verifier-access-certificate" "DIGG Wallet Verifier Access Certificate CA"

PID_ISSUER_CA_KEY="$CA_DIR/pid-issuer/ca_private_key.pem"
PID_ISSUER_CA_PEM="$CA_DIR/pid-issuer/ca.pem"
WALLET_PROVIDER_CA_KEY="$CA_DIR/wallet-provider/ca_private_key.pem"
WALLET_PROVIDER_CA_PEM="$CA_DIR/wallet-provider/ca.pem"
TRUST_SOURCE_CA_KEY="$CA_DIR/trust-source/ca_private_key.pem"
TRUST_SOURCE_CA_PEM="$CA_DIR/trust-source/ca.pem"
VERIFIER_CA_KEY="$CA_DIR/verifier-access-certificate/ca_private_key.pem"
VERIFIER_CA_PEM="$CA_DIR/verifier-access-certificate/ca.pem"

# Use a separate temporary area after the per-CA CRL generation above.
SERVICE_TMP_DIR="$TMP_DIR/services"
mkdir -p "$SERVICE_TMP_DIR"

function generate_service_cert_ec() {
  local target_subdir="${1}"
  local cert_name="${2}"
  local alias_name="${3}"
  local password="${4}"
  local cn="${5}"
  local sans="${6}"
  local service_cnf_file="${7}"
  local ca_pem="${8}"
  local ca_key="${9}"
  local ca_name="${10}"

  local target_dir="$CERT_DIR/$target_subdir"
  mkdir -p "$target_dir"

  echo "Processing $cert_name (EC) for $target_subdir..."

  export crl_dp="URI:http://trust-source/$ca_name/revocation-list.pem"
  export aia_url="URI:http://trust-source/$ca_name/ca.pem"

  # Create temporary config for CSR
  local cnf_file="$SERVICE_TMP_DIR/$cert_name.cnf"
  export cn sans
  envsubst <"$SCRIPT_DIR/templates/$service_cnf_file" >"$cnf_file"

  local key_file="$SERVICE_TMP_DIR/$cert_name.key"
  local csr_file="$SERVICE_TMP_DIR/$cert_name.csr"
  local crt_file="$SERVICE_TMP_DIR/$cert_name.crt"
  local p12_file="$target_dir/$cert_name.p12"

  # Generate EC Private Key (P-256)
  openssl ecparam -name prime256v1 -genkey -noout -out "$key_file"

  # Generate CSR
  openssl req -new -key "$key_file" -out "$csr_file" -config "$cnf_file"

  # Sign Cert
  openssl x509 -req -in "$csr_file" \
    -CA "$ca_pem" -CAkey "$ca_key" \
    -out "$crt_file" -days 825 -sha256 \
    -extfile "$cnf_file" -extensions v3_req \
    -set_serial "0x$(openssl rand -hex 16)"

  # Create P12
  rm -f "$p12_file"
  openssl pkcs12 -export \
    -in "$crt_file" -inkey "$key_file" -certfile "$ca_pem" \
    -out "$p12_file" \
    -name "$alias_name" \
    -passout "pass:$password"

  create_license "$p12_file"

  # Save cert for trust store use by other services
  cp "$crt_file" "$SERVICE_TMP_DIR/$cert_name.crt.trust"
}

# --- Service Certificates ---

# 1. PID Issuer
generate_service_cert_ec "issuer" "pid_issuer" "pid_issuer" "$PID_ISSUER_KEYSTORE_PASSWORD" "PID Issuer (Ecosystem)" "$ISSUER_SANS" service.cnf "$PID_ISSUER_CA_PEM" "$PID_ISSUER_CA_KEY" pid-issuer

# Add nonce-encryption and request-encryption keys to pid_issuer.p12
# (these are transient stores merged into pid_issuer.p12 below, so they share its password)
generate_service_cert_ec "issuer" "nonce" "nonce-encryption" "$PID_ISSUER_KEYSTORE_PASSWORD" "nonce-encryption" "DNS.1:localhost" encryption.cnf "$PID_ISSUER_CA_PEM" "$PID_ISSUER_CA_KEY" pid-issuer
generate_service_cert_ec "issuer" "request" "request-encryption" "$PID_ISSUER_KEYSTORE_PASSWORD" "request-encryption" "DNS.1:localhost" encryption.cnf "$PID_ISSUER_CA_PEM" "$PID_ISSUER_CA_KEY" pid-issuer

keytool -importkeystore -srckeystore "$CERT_DIR/issuer/nonce.p12" -srcstoretype PKCS12 -srcstorepass "$PID_ISSUER_KEYSTORE_PASSWORD" -destkeystore "$CERT_DIR/issuer/pid_issuer.p12" -deststoretype PKCS12 -deststorepass "$PID_ISSUER_KEYSTORE_PASSWORD" -noprompt
keytool -importkeystore -srckeystore "$CERT_DIR/issuer/request.p12" -srcstoretype PKCS12 -srcstorepass "$PID_ISSUER_KEYSTORE_PASSWORD" -destkeystore "$CERT_DIR/issuer/pid_issuer.p12" -deststoretype PKCS12 -deststorepass "$PID_ISSUER_KEYSTORE_PASSWORD" -noprompt
rm -f "$CERT_DIR/issuer/nonce.p12" "$CERT_DIR/issuer/request.p12" "$CERT_DIR/issuer/nonce.p12.license" "$CERT_DIR/issuer/request.p12.license"

echo "Generating issuer_wrprc.jwt for PID Issuer (using bash)..."

# Define target paths
JWT_PATH="${PID_ISSUER_OUT}/issuer_wrprc.jwt"
PRE_JWT_PATH="${PID_ISSUER_OUT}/issuer_wrprc.json"

# Extract cert body, removing headers/footers and newlines
CERT_B64=$(grep -v -- '---' "${SERVICE_TMP_DIR}/pid_issuer.crt" | tr -d '\n')

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
echo -n "$SIGNING_INPUT" | openssl dgst -sha256 -sign "${SERVICE_TMP_DIR}/pid_issuer.key" -out "${SERVICE_TMP_DIR}/sig.der"

# Convert DER signature to Raw R||S
PARSED=$(openssl asn1parse -inform DER -in "${SERVICE_TMP_DIR}/sig.der" 2>/dev/null)
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
generate_service_cert_ec "verifier" "verifier-access-certificate" "verifier_access_certificate" "$VERIFIER_ACCESS_CERTIFICATE_KEYSTORE_PASSWORD" "Verifier Backend (Ecosystem)" "$VERIFIER_SANS" service.cnf "$VERIFIER_CA_PEM" "$VERIFIER_CA_KEY" verifier-access-certificate

# 3. Verifier Trust Store
echo "Creating trusted_issuers.p12 for Verifier..."
TRUST_P12="$CERT_DIR/trust-validator/trusted_issuers.p12"
mkdir -p "$CERT_DIR/trust-validator"
rm -f "$TRUST_P12"
keytool -importcert -noprompt -alias pid_issuer -file "$SERVICE_TMP_DIR/pid_issuer.crt.trust" -keystore "$TRUST_P12" -storepass "$TRUST_VALIDATOR_TRUSTED_ISSUERS_PASSWORD" -storetype PKCS12
keytool -importcert -noprompt -alias pid_issuer_ca -file "$PID_ISSUER_CA_PEM" -keystore "$TRUST_P12" -storepass "$TRUST_VALIDATOR_TRUSTED_ISSUERS_PASSWORD" -storetype PKCS12
create_license "$TRUST_P12"

# 4. Wallet Provider
generate_service_cert_ec "wallet-provider" "wallet_provider" "wallet_provider" "$WALLET_PROVIDER_KEYSTORE_PASSWORD" "Wallet Provider (Ecosystem)" "$PROVIDER_SANS" service.cnf "$WALLET_PROVIDER_CA_PEM" "$WALLET_PROVIDER_CA_KEY" wallet-provider

# 5. Trust Source
generate_service_cert_ec "trust-list-signer" "trust_source" "trust_source" "$TRUST_SOURCE_KEYSTORE_PASSWORD" "Trust Source (Ecosystem)" "$TRUST_SOURCE_SANS" signer.cnf "$TRUST_SOURCE_CA_PEM" "$TRUST_SOURCE_CA_KEY" trust-source
cp "$SERVICE_TMP_DIR/trust_source.crt" "$CERT_DIR/trust-list-signer/trust_source_cert.pem"
cp "$SERVICE_TMP_DIR/trust_source.key" "$CERT_DIR/trust-list-signer/trust_source_key.pem"

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

echo -n "$STATUS_SIGNING_INPUT" | openssl dgst -sha256 -sign "${CERT_DIR}/trust-list-signer/trust_source_key.pem" -out "${SERVICE_TMP_DIR}/status_sig.der"

STATUS_PARSED=$(openssl asn1parse -inform DER -in "${SERVICE_TMP_DIR}/status_sig.der" 2>/dev/null)
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
keytool -importcert -noprompt -alias trust_source -file "$SERVICE_TMP_DIR/trust_source.crt.trust" -keystore "$TRUST_VALIDATOR_TRUST_STORE" -storepass "$TRUST_VALIDATOR_TRUST_STORE_PASSWORD" -storetype PKCS12
keytool -importcert -noprompt -alias trust_source_ca -file "$TRUST_SOURCE_CA_PEM" -keystore "$TRUST_VALIDATOR_TRUST_STORE" -storepass "$TRUST_VALIDATOR_TRUST_STORE_PASSWORD" -storetype PKCS12
create_license "$TRUST_VALIDATOR_TRUST_STORE"

# --- Finalization ---

# Ensure container readability
find "$CERT_DIR" -name "*.p12" -exec chmod 644 {} +
find "$CERT_DIR" -name "*.pem" -exec chmod 644 {} +

# Copy CRL to trust-source to be hosted by Nginx
for ca_name in pid-issuer wallet-provider trust-source verifier-access-certificate; do
  mkdir -p "${TRUST_SOURCE_OUT}/${ca_name}"
  cp "$CA_DIR/$ca_name/ca.pem" "${TRUST_SOURCE_OUT}/${ca_name}/ca.pem"
  cp "$CA_DIR/$ca_name/revocation-list.pem" "${TRUST_SOURCE_OUT}/${ca_name}/revocation-list.pem"
  create_license "${TRUST_SOURCE_OUT}/${ca_name}/ca.pem"
  create_license "${TRUST_SOURCE_OUT}/${ca_name}/revocation-list.pem"
done
cp "$CA_DIR/trust-source/revocation-list.pem" "${TRUST_SOURCE_OUT}/revocation-list.pem"
create_license "${TRUST_SOURCE_OUT}/revocation-list.pem"

echo "Done! All certificates and licenses in config/certificates updated for Ecosystem."
