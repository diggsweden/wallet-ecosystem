#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

source_dir='config/certificates/trust-source'

keytool \
  -J-Duser.language=en \
  -importcert \
  -noprompt \
  -alias trust-source \
  -file "${source_dir}/rsacert.pem" \
  -keystore "${source_dir}/rsacert.p12" \
  -storepass pass1234 \
  -storetype PKCS12
