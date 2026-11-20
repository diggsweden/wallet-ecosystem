#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

# PEM file downloaded from PTS registry
pem_file=$1
tmp_dir="target/pts"
private_key_file="$tmp_dir/access-certificate-private-key.key"
p12_ca_file=config/certificates/ca/verifier-access-certificate/ca.pem
p12_file="$tmp_dir/access-certificate.p12"
target_file="config/certificates/pts/verifier-access-certificate.p12"

openssl pkcs12 -export \
  -in "$pem_file" \
  -inkey "$private_key_file" \
  -certfile "$p12_ca_file" \
  -out "$p12_file" \
  -name pts \
  -passout "pass:secret"

cp "$p12_file" "$target_file"
