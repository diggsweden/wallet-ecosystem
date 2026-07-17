#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

target_file='config/trust-source/trusted-pid-issuers.xml'

xmlsec1 \
  --sign \
  --print-debug \
  --privkey-pem:rsakey.pem config/certificates/trust-source/rsakey.pem \
  --output "${target_file}" \
  config/certificates/trust-source/trusted-pid-issuers.template.xml &&
  npx prettier --write "${target_file}"
