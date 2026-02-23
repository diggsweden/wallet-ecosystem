#!/bin/bash
set -e

# Base directories
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
BASE_DIR="$SCRIPT_DIR"
COMMON_DIR="$SCRIPT_DIR/common"

# Ensure common directory exists
mkdir -p "$COMMON_DIR"

# 1. Root CA configuration (in common/rootca)
ROOTCA_DIR="$COMMON_DIR/rootca"
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
else
    echo "Using existing Root CA found in $ROOTCA_DIR"
fi

function generate_service_cert_ec() {
    local target_dir="$1"
    local cert_name="$2"
    local alias_name="$3"
    local password="$4"
    local cn="$5"
    local sans="$6"

    local config_dir="$BASE_DIR/$target_dir"
    mkdir -p "$config_dir"

    echo "Processing $cert_name (EC) for $target_dir..."

    # Create temporary config for CSR
    local cnf_file="$config_dir/$cert_name.cnf"
    cat > "$cnf_file" <<EOF
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
    local key_file="$config_dir/$target_dir/$cert_name.key"
    mkdir -p "$(dirname "$key_file")"
    openssl ecparam -name prime256v1 -genkey -noout -out "$key_file"

    # Generate CSR
    local csr_file="$config_dir/$target_dir/$cert_name.csr"
    mkdir -p "$(dirname "$csr_file")"
    openssl req -new -key "$key_file" -out "$csr_file" -config "$cnf_file"

    # Sign Cert
    local crt_file="$config_dir/$target_dir/$cert_name.crt"
    mkdir -p "$(dirname "$crt_file")"
    openssl x509 -req -in "$csr_file" \
      -CA "$ROOT_PEM" -CAkey "$ROOT_KEY" -CAcreateserial \
      -out "$crt_file" -days 825 -sha256 \
      -extfile "$cnf_file" -extensions v3_req

    # Create P12 (Delete first to ensure fresh alias)
    local p12_file="$config_dir/$cert_name.p12"
    rm -f "$p12_file"
    
    # IMPORTANT: The -name flag sets the Friendly Name (Alias) in the PKCS12 file
    openssl pkcs12 -export \
      -in "$crt_file" -inkey "$key_file" -certfile "$ROOT_PEM" \
      -out "$p12_file" \
      -name "$alias_name" \
      -passout "pass:$password"

    # Save cert for trust store use before removing
    cp "$crt_file" "$BASE_DIR/$target_dir/$cert_name.crt.tmp"

    # Cleanup
    rm "$cnf_file" "$key_file" "$csr_file" "$crt_file"
}

# 1. PID Issuer
generate_service_cert_ec "issuer" "pid_issuer" "pid_issuer" "pass1234" "PID Test Sandbox" "DNS.1:wallet.sandbox.digg.se"

# 2. Verifier Backend
generate_service_cert_ec "verifier" "verifier_backend" "verifier_backend" "pass1234" "Verifier Test Sandbox" "DNS.1:wallet.sandbox.digg.se,DNS.2:walletlocal,DNS.3:*.wallet.local,DNS.4:localhost,IP.1:127.0.0.1"

# 3. Verifier Trust Store
echo "Creating trusted_issuers.p12 for Verifier..."
TRUST_P12="$BASE_DIR/verifier/trusted_issuers.p12"
rm -f "$TRUST_P12"
keytool -importcert -noprompt -alias pid_issuer -file "$BASE_DIR/issuer/pid_issuer.crt.tmp" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12
keytool -importcert -noprompt -alias root_ca -file "$ROOT_PEM" -keystore "$TRUST_P12" -storepass pass1234 -storetype PKCS12

# 4. Wallet Provider
generate_service_cert_ec "wallet-provider" "wallet_provider" "wallet_provider" "pass1234" "Wallet Provider Test Sandbox" "DNS.1:wallet.sandbox.digg.se,DNS.2:walletlocal,DNS.3:localhost,IP.1:127.0.0.1"

# 5. Traefik TLS Certificates
echo "Generating Traefik TLS certificates..."
TRAEFIK_DIR="$BASE_DIR/traefik/certs"
mkdir -p "$TRAEFIK_DIR"
TRAEFIK_KEY="$TRAEFIK_DIR/wallet-key.pem"
TRAEFIK_CSR="$TRAEFIK_DIR/wallet.csr"
TRAEFIK_CRT="$TRAEFIK_DIR/wallet-cert.pem"
TRAEFIK_CNF="$TRAEFIK_DIR/traefik.cnf"

cat > "$TRAEFIK_CNF" <<EOF
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

# 6. Wallet Client Gateway
# SKIPPED FOR NOW - RE-USING OLD keystore-wallet-app-bff-local.p12 with password 'changeit'

# Final Cleanup of temp certs
rm "$BASE_DIR/issuer/pid_issuer.crt.tmp" "$BASE_DIR/verifier/verifier_backend.crt.tmp" "$BASE_DIR/wallet-provider/wallet_provider.crt.tmp"

# Ensure all generated files are readable by Docker containers
find "$BASE_DIR" -name "*.p12" -exec chmod 644 {} +
find "$BASE_DIR" -name "*.pem" -exec chmod 644 {} +

echo "Done! All ecosystem certificates updated (Gateway skipped)."
