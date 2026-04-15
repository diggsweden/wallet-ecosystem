#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

echo "Waiting for services to become healthy..."
# Give services time to initialize (especially Keycloak)
for i in {1..40}; do
  echo "Check attempt $i/40..."
  # Get statuses of all containers that have health checks
  UNHEALTHY=$(podman ps -a --format "{{.Names}} {{.Status}}" | grep -v "healthy" | grep -E "keycloak|wallet-client-gateway|wallet-attribute-attestation|wallet-account|valkey|db" || true)
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
