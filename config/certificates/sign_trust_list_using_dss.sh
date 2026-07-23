#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

if [ x"${WALLET_DSS_COMMAND}" == "x" ]; then
  >&2 echo 'WALLET_DSS_COMMAND is not set!'
  #shellcheck disable=SC2016
  >&2 echo 'Please specify path to wallet-dss, e.g `java -jar $HOME/wallet-dss-cli/target/wallet-dss-cli.jar`'
  exit 1
fi

set -euo pipefail

source_dir='config/certificates/trust-source'

${WALLET_DSS_COMMAND} sign \
  "${source_dir}/trusted-pid-issuers-dss.template.xml" \
  "${source_dir}/rsakey.pem" \
  "${source_dir}/rsacert.pem" >config/trust-source/trusted-pid-issuers-dss.xml
