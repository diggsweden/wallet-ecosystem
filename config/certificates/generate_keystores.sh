#!/bin/bash
set -e

# Base directories
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
CERT_DIR="$SCRIPT_DIR"
BASE_DIR=$(dirname "$SCRIPT_DIR")

# Ensure certificates directory exists
mkdir -p "$CERT_DIR"

# Helper to create license files
function create_license() {
  local target_file="$1"
  cat >"${target_file}.license" <<EOF
# SPDX-FileCopyrightText: 2025 The Wallet Ecosystem Authors
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
  local cnf_file="$target_dir/$cert_name.cnf"
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

  # Generate EC Private Key (P-256)
  local key_file="$target_dir/$cert_name.key"
  openssl ecparam -name prime256v1 -genkey -noout -out "$key_file"

  # Generate CSR
  local csr_file="$target_dir/$cert_name.csr"
  openssl req -new -key "$key_file" -out "$csr_file" -config "$cnf_file"

  # Sign Cert
  local crt_file="$target_dir/$cert_name.crt"
  openssl x509 -req -in "$csr_file" \
    -CA "$ROOT_PEM" -CAkey "$ROOT_KEY" -CAcreateserial \
    -out "$crt_file" -days 825 -sha256 \
    -extfile "$cnf_file" -extensions v3_req

  # Create P12 (Delete first to ensure fresh alias)
  local p12_file="$target_dir/$cert_name.p12"
  rm -f "$p12_file"

  # IMPORTANT: The -name flag sets the Friendly Name (Alias) in the PKCS12 file
  openssl pkcs12 -export \
    -in "$crt_file" -inkey "$key_file" -certfile "$ROOT_PEM" \
    -out "$p12_file" \
    -name "$alias_name" \
    -passout "pass:$password"

  create_license "$p12_file"

  # Save cert for trust store use before removing
  cp "$crt_file" "$target_dir/$cert_name.crt.tmp"

  # Cleanup
  rm "$cnf_file" "$key_file" "$csr_file" "$crt_file"
}

# 1. PID Issuer
generate_service_cert_ec "issuer" "pid_issuer" "pid_issuer" "pass1234" "PID Test Sandbox" "DNS.1:wallet.sandbox.digg.se"

# 2. Verifier Backend
generate_service_cert_ec "verifier" "verifier_backend" "verifier_backend" "pass1234" "Verifier Test Sandbox" "DNS.1:wallet.sandbox.digg.se,DNS.2:walletlocal,DNS.3:*.wallet.local,DNS.4:localhost,IP.1:127.0.0.1"

# 3. Verifier Trust Store
echo "Creating trusted_issuers.p12 for Verifier..."
TRUST_P12="$CERT_DIR/verifier/trusted_issuers.p12"
rm -f "$TRUST_P12"
# Add PID Issuer and Root CA
keytool -importcert -noprompt -alias pid_issuer -file "$CERT_DIR/issuer/pid_issuer.crt.tmp" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
keytool -importcert -noprompt -alias root_ca -file "$ROOT_PEM" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
create_license "$TRUST_P12"

# 4. Wallet Provider
generate_service_cert_ec "wallet-provider" "wallet_provider" "wallet_provider" "pass1234" "Wallet Provider Test Sandbox" "DNS.1:wallet.sandbox.digg.se,DNS.2:walletlocal,DNS.3:localhost,IP.1:127.0.0.1"

# 5. Traefik TLS Certificates
echo "Generating Traefik TLS certificates..."
TRAEFIK_DIR="$CERT_DIR/traefik"
mkdir -p "$TRAEFIK_DIR"
TRAEFIK_KEY="$TRAEFIK_DIR/wallet-key.pem"
TRAEFIK_CSR="$TRAEFIK_DIR/wallet.csr"
TRAEFIK_CRT="$TRAEFIK_DIR/wallet-cert.pem"
TRAEFIK_CNF="$TRAEFIK_DIR/traefik.cnf"

cat >"$TRAEFIK_CNF" <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = SE
O = DIGG
CN = localhost

[v3_req]
subjectAltName = DNS:localhost,DNS:wallet.sandbox.digg.se,DNS:walletlocal,DNS:refimpl-verifier.wallet.local,IP:127.0.0.1
EOF

openssl genrsa -out "$TRAEFIK_KEY" 2048
openssl req -new -key "$TRAEFIK_KEY" -out "$TRAEFIK_CSR" -config "$TRAEFIK_CNF"
openssl x509 -req -in "$TRAEFIK_CSR" -CA "$ROOT_PEM" -CAkey "$ROOT_KEY" -CAcreateserial -out "$TRAEFIK_CRT" -days 825 -sha256 -extfile "$TRAEFIK_CNF" -extensions v3_req
rm "$TRAEFIK_CSR" "$TRAEFIK_CNF"
create_license "$TRAEFIK_KEY"
create_license "$TRAEFIK_CRT"

# 6. Wallet Client Gateway
echo "Updating wallet_client_gateway.p12 (re-using original keys)..."
GW_DIR="$CERT_DIR/wallet-client-gateway"
ORIG_GW_P12="$GW_DIR/keystore-wallet-app-bff-local.p12"
NEW_GW_P12="$GW_DIR/wallet_client_gateway.p12"

if [ -f "$ORIG_GW_P12" ]; then
  rm -f "$NEW_GW_P12"
  keytool -importkeystore \
    -srckeystore "$ORIG_GW_P12" -srcstorepass changeit -srcstoretype PKCS12 \
    -destkeystore "$NEW_GW_P12" -deststorepass pass1234 -deststoretype PKCS12 \
    -noprompt
  keytool -importcert -noprompt -alias rootca -file "$ROOT_PEM" -keystore "$NEW_GW_P12" -storepass pass1234 -storetype PKCS12
  create_license "$NEW_GW_P12"
fi

# Final Cleanup
rm -f "$CERT_DIR/issuer/pid_issuer.crt.tmp" "$CERT_DIR/verifier/verifier_backend.crt.tmp" "$CERT_DIR/wallet-provider/wallet_provider.crt.tmp"
[ -f "$ROOTCA_DIR/rootca.srl" ] && create_license "$ROOTCA_DIR/rootca.srl"

# Ensure all generated files are readable by Docker containers
find "$CERT_DIR" -name "*.p12" -exec chmod 644 {} +
find "$CERT_DIR" -name "*.pem" -exec chmod 644 {} +

echo "Done! All certificates and licenses in config/certificates updated."
