#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
CERT_DIR="$SCRIPT_DIR"
ENV_FILE="$SCRIPT_DIR/../../.env"

# Rewrite (or append) a KEY=VALUE line in .env
update_env_file() {
  local var_name="$1" value="$2"
  local tmp
  tmp=$(mktemp)
  if [[ -f "$ENV_FILE" ]]; then
    awk -v k="$var_name" -v v="$value" -F= '
      BEGIN{done=0}
      $1==k{print k"="v; done=1; next}
      {print}
      END{if(!done) print k"="v}
    ' "$ENV_FILE" >"$tmp"
  else
    printf '%s=%s\n' "$var_name" "$value" >"$tmp"
  fi
  mv "$tmp" "$ENV_FILE"
}

# Each ecosystem keystore/truststore is a distinct PKI artifact and gets its own password.
# Every run generates a fresh random password (never reused from a previous run) and
# saves it to .env so docker-compose can use the same value the keystore was created with.
generate_password() {
  local var_name="$1"
  local new_password
  new_password=$(openssl rand -hex 24)
  printf -v "$var_name" '%s' "$new_password"
  export "${var_name?}"
  update_env_file "$var_name" "$new_password"
  echo "Generated a new $var_name and saved it to $ENV_FILE"
}

generate_password PID_ISSUER_KEYSTORE_PASSWORD
generate_password VERIFIER_KEYSTORE_PASSWORD
generate_password WALLET_PROVIDER_KEYSTORE_PASSWORD
generate_password TRUST_SOURCE_KEYSTORE_PASSWORD
generate_password TRUST_VALIDATOR_TRUSTED_ISSUERS_PASSWORD
generate_password TRUST_VALIDATOR_TRUST_STORE_PASSWORD

# 1. Ecosystem Service URLs
export CRL_DP="URI:http://trust-source/revocation-list.pem"
export STATUS_LIST_URL="http://trust-source/signed/status-list.jwt"
export AIA_URL="URI:http://trust-source/rootca.crt"

# 2. Ecosystem Output Directories
export PID_ISSUER_OUT="${CERT_DIR}/../pid-issuer"
export TRUST_SOURCE_OUT="${CERT_DIR}/../trust-source"
export LOTE_OUT_FILE="${TRUST_SOURCE_OUT}/signed/trusted-entities.json"

# 3. Ecosystem SANs
export VERIFIER_SANS="DNS.1:localhost,DNS.2:verifier-backend,DNS.3:refimpl-verifier-backend,DNS.4:10.0.2.2"
export PROVIDER_SANS="DNS.1:localhost,DNS.2:wallet-provider"
export ISSUER_SANS="DNS.1:localhost,DNS.2:pid-issuer"
export TRUST_SOURCE_SANS="DNS.1:localhost,DNS.2:trust-source"

echo "Generating Ecosystem Keystores..."
bash "$SCRIPT_DIR/scripts/generate_keystores.sh"

echo "Generating Ecosystem LoTE..."
bash "$SCRIPT_DIR/scripts/generate_lote.sh"

echo "Ecosystem certificates generated successfully."
