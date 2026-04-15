#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

echo "Waiting for services to become healthy..."
REGEX_FOR_ALL_SERVICES=$(yq -r '.services | to_entries | .[].key' docker-compose.yaml | sort | xargs | tr ' ' '|')
REGEX_FOR_SERVICES_WITH_HEALTH_CHECKS='(keycloak|wallet-client-gateway|wallet-attribute-attestation|wallet-account|valkey|db) .*healthy'
REGEX_FOR_INIT_CONTAINERS='keycloak-init Exited \(0\)'
REGEX_FOR_OTHERS='(refimpl-verifier-backend|wallet-provider|pid-issuer|traefik|demo-verifier) Up'
# Give services time to initialize (especially Keycloak)
for i in {1..40}; do
  echo "Check attempt $i/40..."
  # Get statuses of all containers that have health checks
  UNHEALTHY=$(
    podman ps -a --format "{{.Names}} {{.Status}}" |
      grep -E "$REGEX_FOR_ALL_SERVICES" |
      grep -v -E "($REGEX_FOR_SERVICES_WITH_HEALTH_CHECKS)|($REGEX_FOR_INIT_CONTAINERS)|($REGEX_FOR_OTHERS)" || true
  )
  if [ -z "$UNHEALTHY" ]; then
    echo "All required services are healthy!"
    podman ps
    exit 0
  fi
  echo "Waiting for services: $UNHEALTHY"
  sleep 10
done
echo "Timeout waiting for services to become healthy"
podman ps
podman compose logs
exit 1
