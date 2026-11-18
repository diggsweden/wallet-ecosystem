// SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
//
// SPDX-License-Identifier: CC0-1.0

import { execFileSync } from "node:child_process";
import { createPrivateKey, sign, verify, X509Certificate } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const directory = new URL(
  "../config/certificates/verifier-registration-certificate/",
  import.meta.url,
);
const [headerPart, payloadPart] = readFileSync(
  new URL("registration_certificate.jwt", directory),
  "utf8",
)
  .trim()
  .split(".");
const header = JSON.parse(Buffer.from(headerPart, "base64url").toString());
const claims = JSON.parse(Buffer.from(payloadPart, "base64url").toString());

if (header.alg !== "ES256" || header.typ !== "rc-wrp+jwt") {
  throw new Error(
    "Expected an ES256 registration certificate of type rc-wrp+jwt",
  );
}
if (!process.env.VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD) {
  throw new Error("Set VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD");
}

let privateKeyPem;
try {
  privateKeyPem = execFileSync(
    "openssl",
    [
      "pkcs12",
      "-in",
      fileURLToPath(
        new URL("verifier-registration-certificate.p12", directory),
      ),
      "-nocerts",
      "-noenc",
      "-passin",
      "env:VERIFIER_REGISTRATION_CERTIFICATE_KEYSTORE_PASSWORD",
    ],
    { stdio: ["ignore", "pipe", "pipe"] },
  );
} catch {
  // Do not include subprocess output, which could contain private key material.
  throw new Error(
    "Could not read the registration signing key from its keystore",
  );
}
const privateKey = createPrivateKey(privateKeyPem);
privateKeyPem.fill(0);

// Expire only the JWT. Its signing certificate and trusted chain remain current.
// Fixed dates keep this a negative test regardless of when PKI is regenerated.
claims.iat = 946598400; // 1999-12-31 00:00:00 UTC
claims.exp = 946684800; // 2000-01-01 00:00:00 UTC
claims.intended_use_id = "expired-registration";
const signingInput = `${headerPart}.${Buffer.from(JSON.stringify(claims)).toString("base64url")}`;
const signature = sign("sha256", Buffer.from(signingInput), {
  key: privateKey,
  dsaEncoding: "ieee-p1363",
});
const leaf = new X509Certificate(Buffer.from(header.x5c[0], "base64"));
if (
  !verify(
    "sha256",
    Buffer.from(signingInput),
    {
      key: leaf.publicKey,
      dsaEncoding: "ieee-p1363",
    },
    signature,
  )
) {
  throw new Error(
    "Registration signing key does not match the JWT certificate",
  );
}

const jwt = `${signingInput}.${signature.toString("base64url")}`;
writeFileSync(
  new URL("expired_registration_certificate.jwt", directory),
  `${jwt}\n`,
);
writeFileSync(
  new URL("expired_registration_certificate.env", directory),
  `VERIFIER_INTENDEDUSES_1_REGISTRATIONCERTIFICATE=${jwt}\n`,
);
