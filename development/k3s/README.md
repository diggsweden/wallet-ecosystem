# k3s Istio local environment

This directory is a Kubernetes version of the core local Compose stack, using
Istio for ingress/routing and OpenTelemetry Operator for Java
auto-instrumentation.

The first slice contains:

- `wallet-account`
- `wallet-account-db`
- `wallet-provider`
- `wallet-client-gateway`
- `wallet-client-gateway-valkey`
- `keycloak`
- `trust-validator`
- `refimpl-verifier-backend`
- `demo-verifier`
- `pid-issuer`
- OpenTelemetry Collector
- OpenTelemetry Java `Instrumentation`
- Istio `Gateway` and `VirtualService` routing
- Grafana, Loki, and Tempo for local observability

## Prerequisites

- k3s or another local Kubernetes cluster
- `kubectl`
- `helm`
- Istio installed with an ingress gateway
- cert-manager installed
- OpenTelemetry Operator installed

Example local install:

```text
istioctl install --set profile=demo -y
kubectl apply -f \
  https://github.com/cert-manager/cert-manager/releases/latest/download/cert-manager.yaml
kubectl apply -f \
  https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml
```

Optional but useful on k3s:

```text
kubectl top nodes
kubectl get apiservice v1beta1.metrics.k8s.io
```

If `kubectl top` reports that `metrics.k8s.io/v1beta1` is unavailable, fix
metrics-server before debugging resource usage. On k3s this is usually provided
by the bundled metrics-server addon.

## Observability backends

Install the local Grafana, Loki, Tempo, and Prometheus stack into the `observability`
namespace:

```text
kubectl create namespace observability --dry-run=client -o yaml \
  | kubectl apply -f -

helm repo add grafana https://grafana.github.io/helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install tempo grafana/tempo \
  --namespace observability \
  --values development/k3s/observability/tempo-values.yml

helm upgrade --install loki grafana/loki \
  --namespace observability \
  --values development/k3s/observability/loki-values.yml

helm upgrade --install prometheus prometheus-community/prometheus \
  --namespace observability \
  --values development/k3s/observability/prometheus-values.yml

helm upgrade --install grafana grafana/grafana \
  --namespace observability \
  --values development/k3s/observability/grafana-values.yml
```

Check the backends:

```text
kubectl -n observability get pods,svc
```

Grafana is configured with Tempo, Loki, and Prometheus datasources. The local admin
credentials from the values file are `admin` / `admin`.

The OpenTelemetry Collector now exports OTLP metrics on a Prometheus scrape
endpoint at `otel-collector-appmetrics:9464`. That is the current app-metrics
path in k3s. `wallet-bff` does not expose a working native `/metrics` endpoint
in the current image, so it is intentionally not scraped directly.

## Secrets

Create the certificate secrets used by the current k3s slice:

```text
kubectl create namespace wallet-ecosystem-local --dry-run=client -o yaml \
  | kubectl apply -f -
kubectl -n wallet-ecosystem-local create secret generic \
  wallet-provider-certificate \
  --from-file=wallet_provider.p12=\
config/certificates/wallet-provider/wallet_provider.p12
kubectl -n wallet-ecosystem-local create secret generic \
  verifier-backend-certificate \
  --from-file=verifier_backend.p12=\
config/certificates/verifier/verifier_backend.p12
kubectl -n wallet-ecosystem-local create secret generic \
  pid-issuer-certificate \
  --from-file=pid_issuer.p12=\
config/certificates/issuer/pid_issuer.p12
kubectl -n wallet-ecosystem-local create secret generic \
  trust-validator-certificate \
  --from-file=trusted_issuers.p12=\
config/certificates/trust-validator/trusted_issuers.p12
```

The `keycloak-init` job mounts the realm import files from the local checkout
using a `hostPath` volume. This is intentional for the local k3s setup and
assumes the repo lives at:

```text
/home/digg/development/github.com/wallet-ecosystem
```

## Bootstrap

To create namespaces, secrets, observability backends, and apply the manifests
in one step:

```text
development/k3s/bootstrap.sh
```

Set `INSTALL_OBSERVABILITY=false` if you want to skip Grafana, Loki, and Tempo.

## Apply

```text
kubectl apply -f development/k3s/namespace.yml
kubectl apply -f development/k3s/platform/
kubectl apply -f development/k3s/databases/
kubectl apply -f development/k3s/services/
```

## Local custom images

If you want to test a local code change from a sibling repo, build a local image,
import it into k3s containerd, and point the manifest at that image tag.

For example, for `wallet-client-gateway`:

```text
cd /home/digg/development/github.com/wallet-client-gateway
mvn -DskipTests -Dcheckstyle.skip=true -Dformatter.skip=true package
```

Build a local runtime image:

```text
podman build -t localhost/wallet-client-gateway:wua-span .
```

Make the image visible to k3s:

```text
podman save -o /tmp/wallet-client-gateway-wua-span.tar \
  localhost/wallet-client-gateway:wua-span
sudo k3s ctr images import /tmp/wallet-client-gateway-wua-span.tar
```

Then update the k3s manifest to use the local tag, for example in
`development/k3s/services/wallet-client-gateway.yml`:

```text
image: localhost/wallet-client-gateway:wua-span
```

Apply and verify the rollout:

```text
kubectl apply -f development/k3s/services/wallet-client-gateway.yml
kubectl -n wallet-ecosystem-local rollout restart deploy/wallet-client-gateway
kubectl -n wallet-ecosystem-local rollout status deploy/wallet-client-gateway
kubectl -n wallet-ecosystem-local get pod \
  -l app.kubernetes.io/name=wallet-client-gateway \
  -o jsonpath='{range .items[*]}{.metadata.name} {.status.phase} {.spec.containers[0].image} {"\n"}{end}'
```

The important detail is that Podman and k3s do not share the same image store.
Building a local image in Podman is not enough by itself; the image must also be
imported into k3s containerd before Kubernetes can run it.

## Access

Forward the Istio ingress gateway locally:

```text
kubectl -n wallet-ecosystem-local port-forward \
  service/wallet-gateway-istio 8080:80
```

Then call:

```text
curl http://localhost:8080/wallet-account/actuator/health
curl http://localhost:8080/wallet-client-gateway/actuator/health
```

On k3s, the generated `wallet-gateway-istio` service may also be reachable
through its `NodePort`. Find it with:

```text
kubectl -n wallet-ecosystem-local get svc wallet-gateway-istio
```

For example, if port `80` maps to NodePort `32188`:

```text
curl -i http://localhost:32188/wallet-account/api-info
```

For the identity and verifier slice, use a stable local URL that matches the
configured public metadata:

```text
kubectl -n wallet-ecosystem-local port-forward \
  service/wallet-gateway-istio 28080:80
```

Then use:

```text
http://localhost:28080/idp
http://localhost:28080/pid-issuer
http://localhost:28080/refimpl-verifier-backend
http://localhost:28080/demo-verifier
```

## Telemetry verification

Check application telemetry in the collector logs:

```text
kubectl -n wallet-ecosystem-local logs \
  -l app.kubernetes.io/name=otel-collector-collector \
  --tail=200
```

You should see trace output for services such as `wallet-account`,
`wallet-provider`, and `wallet-client-gateway`. A request to
`/wallet-account/api-info` should also produce a `wallet-account` log record
with the request path, response status, and correlation ID.

The collector exports traces to Tempo and logs to Loki in the `observability`
namespace:

```text
tempo.observability.svc.cluster.local:4317
http://loki.observability.svc.cluster.local:3100/otlp
```

If the collector logs contain lookup failures for
`tempo.wallet-observability.svc.cluster.local` or
`loki.wallet-observability.svc.cluster.local`, the collector is using an old
namespace and needs to be reapplied from
`development/k3s/platform/otel-collector.yml`.

To query the backends directly:

```text
kubectl -n observability port-forward svc/tempo 3200:3200
curl 'http://127.0.0.1:3200/api/search?limit=20'
```

```text
kubectl -n observability port-forward svc/loki 3100:3100
curl 'http://127.0.0.1:3100/loki/api/v1/labels'
curl -G 'http://127.0.0.1:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={service_name="wallet-account"}' \
  --data-urlencode 'limit=5'
```

## Tests

Run the integration tests supported by this k3s slice:

```text
just test-k3s
```

The recipe discovers the `wallet-gateway-istio` NodePort and exports the
service base URI environment variables used by the tests. It currently runs
the tests tagged `k3s`, covering `wallet-account`, `wallet-provider`, and
`wallet-client-gateway`.

The verifier and issuer tests need the extra Keycloak, PID issuer, trust
validator, and verifier manifests to be healthy, and they should run against
the `28080` gateway port-forward because those services publish public URLs
with that base.

Run that slice with:

```text
just test-k3s-identity
```

The recipe starts a temporary `kubectl port-forward` to `28080`, exports the
identity and verifier base URI environment variables, and runs the tests tagged
`k3s-identity`, covering Keycloak, PID issuer, verifier backend, verifier
frontend, and the end-to-end issuance and presentation flow.

## R2PS Slice

`wallet-bff` and `hsm-worker` are not just two extra deployments. They depend
on:

- a local Kafka broker
- the `init-kafka` topic bootstrap job
- OPAQUE and SoftHSM environment material from `.env.opaque` and `.env.softhsm`
- writable token storage for SoftHSM

`wallet-client-gateway` already points at `http://wallet-bff:8088` in the k3s
manifests, so the R2PS slice is the next logical batch after the identity and
verifier services are stable.

The current k3s manifests now include that slice:

- `kafka-1`
- `init-kafka`
- `wallet-bff`
- `hsm-worker`

Bootstrap creates a Kubernetes secret named `hsm-worker-env` from:

- `.env.opaque`
- `.env.softhsm`

and prepares local token storage at:

```text
development/k3s/state/softhsm-tokens
```

Apply or re-apply with:

```text
development/k3s/bootstrap.sh
```

or manually:

```text
kubectl -n wallet-ecosystem-local delete job init-kafka --ignore-not-found
kubectl apply -f development/k3s/services/kafka.yml
kubectl apply -f development/k3s/services/wallet-bff.yml
kubectl apply -f development/k3s/services/hsm-worker.yml
```

## Notes

This is not intended as a final production manifest layout. It is a local
integration environment that keeps the service names, base paths, and runtime
configuration close to `docker-compose.yaml`, while replacing Traefik labels
with Istio routing resources.
