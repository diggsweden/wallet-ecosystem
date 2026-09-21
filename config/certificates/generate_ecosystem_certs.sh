#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
CERT_DIR="$SCRIPT_DIR"
ENV_FILE="$SCRIPT_DIR/../../.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Environment file not found: $ENV_FILE" >&2
  echo "Copy .env.example to .env before generating certificates." >&2
  exit 1
fi

# Load .env and export its variables so the child generator can use them
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

# 1. Ecosystem Service URLs
export STATUS_LIST_URL="http://trust-source/signed/status-list.jwt"

# 2. Ecosystem Output Directories
export PID_ISSUER_OUT="${CERT_DIR}/../pid-issuer"
export TRUST_SOURCE_OUT="${CERT_DIR}/../trust-source"
export LOTE_OUT_FILE="${TRUST_SOURCE_OUT}/signed/trusted-entities.json"

# 3. Ecosystem SANs
export VERIFIER_SANS="DNS.1:localhost,DNS.2:verifier-backend,DNS.3:refimpl-verifier-backend,DNS.4:10.0.2.2,URI.1:https://localhost/demo-verifier"
export PROVIDER_SANS="DNS.1:localhost,DNS.2:wallet-provider"
export ISSUER_SANS="DNS.1:localhost,DNS.2:pid-issuer"
export TRUST_SOURCE_SANS="DNS.1:localhost,DNS.2:trust-source"

echo "Generating Ecosystem Keystores..."
bash "$SCRIPT_DIR/scripts/generate_keystores.sh"

echo "Generating Ecosystem LoTE..."
bash "$SCRIPT_DIR/scripts/generate_lote.sh"

echo "Ecosystem certificates generated successfully."
