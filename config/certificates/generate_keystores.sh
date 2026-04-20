#!/bin/bash
set -e

# Base directories
CERT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
TMP_DIR="$CERT_DIR/tmp"

# Cleanup temporary files on exit
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR"

# Helper to create REUSE license files
function create_license() {
  local target_file="$1"
  cat >"${target_file}.license" <<EOF
# SPDX-FileCopyrightText: 2025 Digg - Agency for Digital Government
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
  openssl req -x509 -new -nodes -key "$ROOT_KEY" \
    -sha256 -days 3650 \
    -out "$ROOT_PEM" \
    -subj "/C=SE/O=DIGG/CN=DIGG Wallet Ecosystem Root CA"
  create_license "$ROOT_KEY"
  create_license "$ROOT_PEM"
else
  echo "Using existing Root CA found in $ROOTCA_DIR"
fi

function generate_service_cert_ec() {
  local target_subdir="$1"
  local cert_name="$2"
  local alias_name="$3"
  local password="$4"
  local cn="$5"
  local sans="$6"

  local target_dir="$CERT_DIR/$target_subdir"
  mkdir -p "$target_dir"

  echo "Processing $cert_name (EC) for $target_subdir..."

  # Create temporary config for CSR
  local cnf_file="$TMP_DIR/$cert_name.cnf"
  cat >"$cnf_file" <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = SE
O = DIGG
CN = $cn

[v3_req]
subjectAltName = $sans
EOF

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
generate_service_cert_ec "issuer" "pid_issuer" "pid_issuer" "pass1234" "PID Issuer (Ecosystem)" "DNS.1:localhost,DNS.2:pid-issuer"

# 2. Verifier Backend
generate_service_cert_ec "verifier" "verifier_backend" "verifier_backend" "pass1234" "Verifier Backend (Ecosystem)" "DNS.1:localhost,DNS.2:verifier-backend,DNS.3:refimpl-verifier-backend,DNS.4:10.0.2.2"

# 3. Verifier Trust Store
echo "Creating trusted_issuers.p12 for Verifier..."
TRUST_P12="$CERT_DIR/verifier/trusted_issuers.p12"
rm -f "$TRUST_P12"
keytool -importcert -noprompt -alias pid_issuer -file "$TMP_DIR/pid_issuer.crt.trust" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
keytool -importcert -noprompt -alias root_ca -file "$ROOT_PEM" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
create_license "$TRUST_P12"

# 4. Wallet Provider
generate_service_cert_ec "wallet-provider" "wallet_provider" "wallet_provider" "pass1234" "Wallet Provider (Ecosystem)" "DNS.1:localhost,DNS.2:wallet-provider"

# --- Finalization ---

# License for serial file if it exists
[ -f "$ROOTCA_DIR/rootca.srl" ] && create_license "$ROOTCA_DIR/rootca.srl"

# Ensure container readability
find "$CERT_DIR" -name "*.p12" -exec chmod 644 {} +
find "$CERT_DIR" -name "*.pem" -exec chmod 644 {} +

echo "Done! All certificates and licenses in config/certificates updated for Ecosystem."
