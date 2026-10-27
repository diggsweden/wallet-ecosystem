# Ecosystem Certificates

## Generate Internal Service Certificates and Keystores

The ecosystem keeps development CAs, keystores and signed trust documents in Git
so a fresh checkout and CI can start the services. Internal PKI is separate from
the Traefik/mkcert TLS certificates managed by `just certs-tls`.

### Generate PKI artifacts

Copy `.env.example` to `.env` if needed, then run from the ecosystem root:

```shell
just pki-generate
```

`pki-generate` runs the `wallet-pki-tool` container, pulling the image when absent.
It passes `.env` through `--env-file` and uses
[ecosystem.yaml](ecosystem.yaml). Ordinary `just up` does not regenerate keys.
`just certs` generates TLS and service PKI.

The PKI image and `.env` path are fixed in the [justfile](../../justfile).

The generator runs as the host user. It mounts this repository at `/workspace`
and existing `config/certificates/ca` material read-only at
`/workspace/config/certificates/ca`, preserving the configured relative paths.
Generation reuses the existing roots and fails if their
required files are missing or invalid. CA initialization or replacement is a
separate, deliberate PKI-tool operation with a writable authority mount.

### Run other PKI commands

Pass arguments directly to the tool with `just pki`:

```shell
just pki --help
just pki verifier access generate --config config/certificates/ecosystem.yaml
```

### Generated artifacts

Each generation replaces keys and artifacts for the PID issuer, wallet
attestation issuer, verifier access, verifier registration and trust source.

Output paths and aliases match the Compose consumers. Public certificates,
CRLs and signed documents are container-readable. Static trust-source assets
and the verifier CPS remain in place. The signed status-list JWT is an all-valid
development fixture. Increase `trustSource.lote.sequenceNumber` in `ecosystem.yaml`
when publishing an updated LoTE.

Keep regenerated development artifacts and their existing `.license` sidecars
in Git. Before generating committed fixtures, set the passwords in `.env` to
the development values from `.env.example`. Use the same passwords when running
those fixtures.
Recreate the consumers after replacing their mounted files and run the suite:

```shell
just down
just pull
just up
just test
```
