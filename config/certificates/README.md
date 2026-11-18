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

Utvecklarportalen localhost certificates are generated first using
[utvecklarportalen.yaml](utvecklarportalen.yaml), before the ecosystem LoTE.

The PKI image and `.env` path are fixed in the [justfile](../../justfile).

The generator runs as the host user. It mounts this repository at `/workspace`
and existing `config/certificates/ca` material read-only at
`/workspace/config/certificates/ca`, preserving the configured relative paths.
Generation reuses existing CAs and fails if they are missing or invalid.
CA initialization and replacement are separate PKI-tool operations.

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

## Testcentralen: expired registration certificate

The verifier has a second intended use, `expired-registration`, for the
demo-verifier's Testcentralen page. Its registration JWT expired on `2000-01-01`,
but has a valid signature and the same signing certificate, trusted chain,
organisation and registered attributes as the normal registration JWT. The access
certificate and authorization request signing remain valid, so the wallet can
reach its registration-expiry check.

`just pki-generate` also regenerates this fixture using the current registration
keystore. This step requires Node.js (managed by mise) and OpenSSL 3. To regenerate
only the expired JWT after replacing the normal registration JWT or keystore:

```shell
just pki-generate-expired-registration
```

Commit both `expired_registration_certificate.jwt` and
`expired_registration_certificate.env` with their license sidecars. As with the
normal fixtures, regenerate using the development passwords from `.env.example`
when preparing files for Git. Recreate `refimpl-verifier-backend` after updating
its environment files; restarting alone does not reload them.

After releasing the web change, update the `demo-verifier` image tag and digest in
`docker-compose.yaml` to a release containing `/testcentralen`.
Open that page, then scan its QR code with an enrolled wallet containing a PID,
or open the wallet on the same device. The wallet must block sharing and show an
understandable error. Assess the message in the wallet; the website does not
automatically determine whether the test passed. The other centrals use intended
use `1` and retain their normal registration certificate.
