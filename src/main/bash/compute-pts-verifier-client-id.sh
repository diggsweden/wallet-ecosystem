#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

tmp_dir="${1:-target/pts}"
input_file="config/certificates/pts/verifier-access-certificate.p12"

openssl pkcs12 -in "${input_file}" -clcerts -nokeys \
  -passin "pass:secret" |
  openssl x509 -outform DER |
  openssl dgst -sha256 -binary |
  openssl base64 -A |
  tr '+/' '-_' | tr -d '='
