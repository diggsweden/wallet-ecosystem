# Ecosystem Certificates

## Generate Internal Service Certificates and Keystores

The ecosystem uses separate local self-signed CAs for the PID Issuer, Wallet Provider, Trust Source, Verifier access certificates and Verifier registration certificate trust domains.
These certificates are separate from the Traefik/mkcert TLS certificate and are managed via `wallet-pki-tool` using the declarative configuration in `config/certificates/ecosystem.yaml`.

### Automated Generation

To (re)generate all ecosystem certificates, keystores, and trust lists, run:

```shell
just certs
```

This command:

* Issues Traefik TLS certificates using `mkcert`.
* Executes `wallet-pki-tool` with `config/certificates/ecosystem.yaml`.
* Generates one EC (P-256) CA per application trust domain (if not already present).
* Issues certificates and generates PKCS#12 keystores for **PID Issuer**, **Verifier Access**, **Verifier Registration**, **Wallet Provider**, and **Trust Source**.
* Generates a local WRPRC registration certificate as a signed JWT (`registration_certificate.jwt` and `.env`).
* Generates the signed LoTE (ETSI TS 119 602) and OAuth Status List JWT for the Trust Source.
* Mirrors CA certificates and CRLs for hosting via the Trust Source Nginx service.
