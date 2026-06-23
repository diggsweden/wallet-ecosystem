#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NAMESPACE="wallet-ecosystem-local"
OBS_NAMESPACE="observability"
GATEWAY_PORT="${GATEWAY_PORT:-28080}"
INSTALL_OBSERVABILITY="${INSTALL_OBSERVABILITY:-true}"

create_or_update_secret() {
  local name="$1"
  local file_key="$2"
  local file_path="$3"

  kubectl -n "${NAMESPACE}" create secret generic "${name}" \
    --from-file="${file_key}=${file_path}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

create_or_update_env_secret() {
  local name="$1"
  shift

  local args=()
  local env_file
  for env_file in "$@"; do
    args+=(--from-env-file="${env_file}")
  done

  kubectl -n "${NAMESPACE}" create secret generic "${name}" \
    "${args[@]}" \
    --dry-run=client -o yaml | kubectl apply -f -
}

echo "Creating namespaces"
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace "${OBS_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

echo "Preparing local state directories"
mkdir -p "${ROOT_DIR}/development/k3s/state/softhsm-tokens"

echo "Creating certificate secrets"
create_or_update_secret \
  wallet-provider-certificate \
  wallet_provider.p12 \
  "${ROOT_DIR}/config/certificates/wallet-provider/wallet_provider.p12"
create_or_update_secret \
  verifier-backend-certificate \
  verifier_backend.p12 \
  "${ROOT_DIR}/config/certificates/verifier/verifier_backend.p12"
create_or_update_secret \
  pid-issuer-certificate \
  pid_issuer.p12 \
  "${ROOT_DIR}/config/certificates/issuer/pid_issuer.p12"
create_or_update_secret \
  trust-validator-certificate \
  trusted_issuers.p12 \
  "${ROOT_DIR}/config/certificates/trust-validator/trusted_issuers.p12"
create_or_update_env_secret \
  hsm-worker-env \
  "${ROOT_DIR}/.env.opaque" \
  "${ROOT_DIR}/.env.softhsm"

if [[ "${INSTALL_OBSERVABILITY}" == "true" ]]; then
  echo "Installing observability stack"
  helm repo add grafana https://grafana.github.io/helm-charts >/dev/null 2>&1 || true
  helm repo update
  helm upgrade --install tempo grafana/tempo \
    --namespace "${OBS_NAMESPACE}" \
    --values "${ROOT_DIR}/development/k3s/observability/tempo-values.yml"
  helm upgrade --install loki grafana/loki \
    --namespace "${OBS_NAMESPACE}" \
    --values "${ROOT_DIR}/development/k3s/observability/loki-values.yml"
  helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
  helm repo update
  helm upgrade --install prometheus prometheus-community/prometheus \
    --namespace "${OBS_NAMESPACE}" \
    --values "${ROOT_DIR}/development/k3s/observability/prometheus-values.yml"
  helm upgrade --install grafana grafana/grafana \
    --namespace "${OBS_NAMESPACE}" \
    --values "${ROOT_DIR}/development/k3s/observability/grafana-values.yml"
fi

echo "Applying manifests"
kubectl apply -f "${ROOT_DIR}/development/k3s/namespace.yml"
kubectl apply -f "${ROOT_DIR}/development/k3s/platform/"
kubectl apply -f "${ROOT_DIR}/development/k3s/databases/"

kubectl -n "${NAMESPACE}" delete job keycloak-init --ignore-not-found
kubectl -n "${NAMESPACE}" delete job init-kafka --ignore-not-found
kubectl apply -f "${ROOT_DIR}/development/k3s/services/"

echo
echo "Bootstrap submitted."
echo "Use this stable gateway URL for the identity/verifier slice:"
echo "  kubectl -n ${NAMESPACE} port-forward service/wallet-gateway-istio ${GATEWAY_PORT}:80"
echo
echo "Wallet service tests still work against the NodePort route:"
echo "  just test-k3s"
