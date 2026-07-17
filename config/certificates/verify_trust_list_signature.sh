#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail
xmlsec1 \
  --verify \
  --pubkey-pem:rsakey.pem config/certificates/trust-source/rsapub.pem \
  config/trust-source/trusted-pid-issuers.xml
