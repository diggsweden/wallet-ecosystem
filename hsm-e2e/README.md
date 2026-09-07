<!--
SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government

SPDX-License-Identifier: CC0-1.0
-->

# hsm-e2e

Android instrumented test of the wallet **HSM key operations** through the real
[`se.digg.wallet:access-mechanism`][am] library and the real
`wallet-client-gateway`:

```text
emulator ─ access-mechanism (OpaqueClient + native OPAQUE)
             │  X-API-KEY + challenge-response SESSION
             ▼
        wallet-client-gateway ─▶ wallet-bff ─▶ hsm-worker ─▶ SoftHSM
```

Standalone Gradle build (not part of `mvn test`); needs an Android emulator
because `access-mechanism` ships its OPAQUE core as an Android-only `.so`. The
backend is the plain ecosystem `../docker-compose.yaml` — nothing extra to build.

## What it checks

`HsmOperationsE2ETest` — each test stands up a fresh device (new gateway account
→ challenge-response session → `registration(pin)`) and re-`authenticate`s
before each mutating call (the worker allows one per OPAQUE session):

| Test | Flow |
|---|---|
| `registers_authenticates_and_lists_the_auto_provisioned_key` | `authenticate` → `listHsmKeys` (≥ 1 auto-provisioned P-256 key) |
| `signs_raw_bytes_and_verifies_against_the_hsm_key` | `sign(kid, bytes)` → verify against the key's JWK |
| `app_style_jws_signed_via_raw_sign_verifies_against_the_hsm_key` | `sign()` + manual JWS assembly (as `wallet-app-android`'s `JwtUtils.signJwtWith`) → verify |
| `creates_then_deletes_an_hsm_key` | `createHsmKey` → list (+1) → `deleteHsmKey` → list (gone) |

`OpaqueClient.signJws()` is **not** tested: the app doesn't use it, and it
assumes a DER signature while the HSM returns P1363 since wallet-r2ps
`23173855c`. Gateway unreachable ⇒ tests skip (`Assume`), not fail.

## Run it

Needs podman, an Android SDK, and a JDK ≤ 21 for Gradle (`hsm-e2e/.mise.toml`
picks one if you use `mise`).

```sh
just up          # the ecosystem
just hsm-test    # boot a headless emulator if needed, run the tests, print a summary
just down
```

`emulator.sh` finds the SDK (`ANDROID_HOME` / `ANDROID_SDK_ROOT` / platform
default), reuses a running emulator or your first AVD, and creates a host-arch
`google_apis` AVD only if you have none. `just hsm-emulator Pixel_9` pins a
specific one. On a Citrix App Protection machine it stops with instructions (that
library segfaults the emulator).

Target another gateway with
`./gradlew connectedCheck -Pgateway.base.url=<url>`.

## CI

`.github/workflows/hsm-e2e.yml` — `workflow_dispatch` and PRs touching
`hsm-e2e/`. Runner's own Android SDK, no third-party actions; system image + AVD
cached. `run-e2e.sh` writes the pass/skip/fail table to the job summary and
emits `::error` annotations.

[am]: https://github.com/diggsweden/android-access-mechanism
