#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

source_dir='config/certificates/trust-source'
target_file='config/trust-source/trusted-pid-issuers.xml'

xmlsec1 \
  --sign \
  --print-debug \
  --privkey-pem:rsakey.pem "${source_dir}/rsakey.pem,${source_dir}/rsacert.pem" \
  --output "${target_file}" \
  "${source_dir}/trusted-pid-issuers.template.xml" &&
  npx prettier --write "${target_file}"
