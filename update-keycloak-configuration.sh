#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 The Wallet Ecosystem Authors
#
# SPDX-License-Identifier: EUPL-1.2

docker run --rm \
  --network wallet-ecosystem-network \
  -e KEYCLOAK_URL="http://keycloak:8080/idp/" \
  -e KEYCLOAK_USER=admin \
  -e KEYCLOAK_PASSWORD=password \
  -e IMPORT_FILES_LOCATIONS='/config/**/*.json' \
  -v "${PWD}/config/keycloak/realms/":/config \
  quay.io/adorsys/keycloak-config-cli@sha256:2d2a0663cf324379d9ffab896db8d00293cd0326151968b319cf166f6eec8fca
