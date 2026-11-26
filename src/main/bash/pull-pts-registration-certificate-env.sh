#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

set -euo pipefail

tmp_dir="${1:-target/pts}"
jwt_url="https://registration.wallet-relying-party-register.se/api/regcert/9232193707/jwt?sid=https%3A%2F%2Flocal.digg.se%2Fpts-verifier-backend"
jwt_file="$tmp_dir/registration-certificate.jwt"
target_file="${2:-$tmp_dir/registration-certificate.env}"

mkdir -p "${tmp_dir}"
curl -s "$jwt_url" >"$jwt_file"
printf 'VERIFIER_INTENDEDUSES_0_REGISTRATIONCERTIFICATE=%s\n' "$(tr -d '\n' <"$jwt_file")" >"$target_file"
