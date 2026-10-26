# Ecosystem Certificates

## Generate Internal Service Certificates and Keystores

The ecosystem keeps development CAs, keystores and signed trust documents in Git
so a fresh checkout and CI can start the services. Internal PKI is separate from
the Traefik/mkcert TLS certificates managed by `just certs`.

### Local PKI container

Check out `wallet-pki-tool` beside this repository, with WAI generation support.
Copy `.env.example` to `.env` if needed, then run from the ecosystem root:

```shell
just pki-build
just pki-generate
```

`pki-build` builds the PKI source checkout into a local image. `pki-generate` runs
that image with `--pull=never`, passes `.env` through `--env-file`, and uses
[pki.yaml](pki.yaml). Ordinary `just up` does not regenerate keys.

The recipes accept these environment overrides:

| Variable | Default | Purpose |
| --- | --- | --- |
| `PKI_SOURCE_DIRECTORY` | `../wallet-pki-tool` | Local source checkout to build |
| `PKI_IMAGE` | `localhost/wallet-pki-tool:local` | Locally built image |
| `PKI_ENV_FILE` | `.env` | Keystore password environment file |

`pki-build` forwards extra arguments to `podman build`. If your network requires
host Maven settings and a custom Java trust store, populate the wrapper cache
with `./mvnw --version` in the PKI checkout, then mount the build inputs:

```shell
just pki-build \
  --volume "$HOME/.m2:/root/.m2" \
  --volume /etc/ssl/certs/java/cacerts:/run/host-cacerts:ro \
  --env MAVEN_OPTS=-Djavax.net.ssl.trustStore=/run/host-cacerts
```

These mounts are available during build steps and are not copied into the image.

The generator runs as the host user. It mounts this repository at `/workspace`
and existing `config/certificates/ca` material read-only at
`/pki-state/authorities`. Generation reuses the existing roots and fails if their
required files are missing or invalid. CA initialization or replacement is a
separate, deliberate PKI-tool operation with a writable authority mount.

### Artifacts and the WP migration

Each generation replaces keys and artifacts for the PID issuer, wallet
attestation issuer, verifier access, verifier registration and trust source.
The LoTE includes both WAI and legacy WP CAs using the wallet issuance service
type. Legacy WP's existing keystore, CA and public files remain unchanged until
retirement; this generator does not renew its identity.

Output paths and aliases match the Compose consumers. Public certificates,
CRLs and signed documents are container-readable. Static trust-source assets
and the verifier CPS remain in place. The signed status-list JWT is an all-valid
development fixture. Increase `trustSource.lote.sequenceNumber` in `pki.yaml`
when publishing an updated LoTE.

Keep regenerated development artifacts and their existing `.license` sidecars
in Git. Use the development passwords from `.env.example` for committed fixtures:

```shell
PKI_ENV_FILE=.env.example just pki-generate
```

Use matching passwords in your local `.env` when running those fixtures.
Recreate the consumers after replacing their mounted files and run the suite:

```shell
just down
just down
just pull
just up
just test
```
