#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

if [ x"${WALLET_DSS_COMMAND:-}" == "x" ]; then
  # Fallback to podman compose if not set
  WALLET_DSS_COMMAND="podman compose run --rm wallet-dss-cli"
fi

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
ROOT_DIR="$SCRIPT_DIR/../.."

cd "$ROOT_DIR"

source_dir="config/certificates/trust-list-signer"
dest_dir="config/trust-source/signed"

mkdir -p "${dest_dir}"

for template in "${source_dir}"/*.template.xml; do
  if [ -f "$template" ]; then
    filename=$(basename -- "$template")
    outname="${filename%.template.xml}.xml"

    ${WALLET_DSS_COMMAND} sign \
      "$template" \
      "${source_dir}/trust_source_key.pem" \
      "${source_dir}/trust_source_cert.pem" >"${dest_dir}/${outname}"
  fi
done
