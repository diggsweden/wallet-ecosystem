# Ecosystem Certificates

## Generate Internal Service Certificates and Keystores

The ecosystem uses separate local self-signed CAs for the PID Issuer, Wallet Provider, Trust Source, Verifier access certificates and Verifier registration certificate trust domains.
These certificates are separate from the Traefik/mkcert TLS certificate and are managed via an automation script.

### Automated Generation

To (re)generate all ecosystem certificates, keystores, and REUSE-compliant license files, run:

```shell
./config/certificates/generate_ecosystem_certs.sh
```

This script:

* Generates one EC (P-256) CA per application trust domain (if not present).
* Issues certificates for **PID Issuer**, **Verifier**, **Wallet Provider**, and **Trust Source**.
* Generates a local WRPRC registration certificate as a signed JWT for the verifier after the keystores are created.
* Creates the **Trust Validator** certificate stores and regenerates the signed LoTE for the PID Issuer, Wallet Provider, and WRPRC Provider.
* Ensures generated keystores and PEM files are readable by Docker containers.
