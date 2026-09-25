# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government

##

## SPDX-License-Identifier: CC0-1.0

## Access Certificate Task

### Task

[uppgiftstexten]

### Current certificate

File:
config/certificates/verifier/verifier_backend.p12

Certificate:

- Subject: CN=Verifier Backend (Ecosystem), ...
- Issuer: CN=DIGG Wallet Ecosystem Root CA, ...
- Key: EC secp256r1
- Signature: SHA256withECDSA
- SAN:
  - localhost
  - verifier-backend
  - refimpl-verifier-backend
  - 10.0.2.2
- EKU:
  - serverAuth
  - clientAuth
- KeyUsage:
  - digitalSignature
  - nonRepudiation
- Certificate Policy:
  - 0.4.0.2042.1.2

### Specifications

ETSI TS 119 411-8:
<https://www.etsi.org/>...

ETSI EN 319 411-1:
<https://www.etsi.org/>...

### Current findings

PASS:

- X.509 v3
- certificate chain
- organizationIdentifier present
- ...

FAIL / needs investigation:

- CPS URI appears to be missing
- SAN does not contain RP contact information
- Certificate Policy OID needs verification
- KeyUsage/EKU need comparison with applicable profile

### Goal

Determine exactly what needs to change in the certificate generation/
configuration and create tests proving compliance.
