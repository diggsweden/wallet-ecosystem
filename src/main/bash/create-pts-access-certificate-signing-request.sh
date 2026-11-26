#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

tmp_dir="target/pts"
config_file="$tmp_dir/access-certificate-config.cnf"
private_key_file="$tmp_dir/access-certificate-private-key.key"
certificate_signing_request_file="$tmp_dir/access-certificate-signing-request.csr"

mkdir -p $tmp_dir

cat >"$config_file" <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = SE
O = DIGG
CN = PTS Verifier Backend (Local)

[v3_req]
subjectAltName = DNS.1:localhost,DNS.2:local.digg.se,DNS.3:pts-verifier-backend
EOF

openssl ecparam -name prime256v1 -genkey -noout -out "$private_key_file"
openssl req -new -key "$private_key_file" -out "$certificate_signing_request_file" -config "$config_file"

cat "$certificate_signing_request_file"
