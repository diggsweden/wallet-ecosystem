#!/bin/bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: EUPL-1.2

echo "Waiting for services to become healthy..."
PROJECT_NAME=$(yq -r '.name' docker-compose.yaml)
IGNORED_TEMPLATE_SERVICES=$(yq -r '.services | to_entries | .[].key | select(test("^x-"))' docker-compose.yaml | sort | xargs)
if [ -n "$IGNORED_TEMPLATE_SERVICES" ]; then
  echo "Ignoring compose template services: $IGNORED_TEMPLATE_SERVICES"
fi
REGEX_FOR_ALL_SERVICES=$(yq -r '.services | to_entries | .[].key | select(test("^x-") | not)' docker-compose.yaml | sort | xargs | tr ' ' '|')
REGEX_FOR_CONTAINER_NAME="^($REGEX_FOR_ALL_SERVICES|$PROJECT_NAME-($REGEX_FOR_ALL_SERVICES)-[0-9]+) "
REGEX_FOR_SERVICES_WITH_HEALTH_CHECKS='(keycloak|wallet-client-gateway|wallet-account|valkey|db|kafka-[0-9]+)(-[0-9]+)? .*healthy'
REGEX_FOR_INIT_CONTAINERS='(keycloak-master-init|init-kafka)(-[0-9]+)? Exited \(0\)'
REGEX_FOR_OTHERS='(refimpl-verifier-backend|wallet-provider|pid-issuer|traefik|demo-verifier|trust-validator|wallet-bff|hsm-worker|kafka-ui)(-[0-9]+)? Up'
# Give services time to initialize (especially Keycloak)
for i in {1..10}; do
  echo "Check attempt $i/10..."
  # Get statuses of all containers that have health checks
  UNHEALTHY=$(
    podman ps -a --format "{{.Names}} {{.Status}}" |
      grep -E "$REGEX_FOR_CONTAINER_NAME" |
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
