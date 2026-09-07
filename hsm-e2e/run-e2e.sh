#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# Runs the hsm-e2e instrumented test (attached device + `just up` ecosystem) and
# prints a per-scenario description and a pass/skip/fail table. Entry point for
# `just hsm-check` and CI. Exit code is Gradle's.

set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Resolve the SDK from the env or a platform default (Linux / macOS / Windows).
for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
  "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/AppData/Local/Android/Sdk"; do
  [ -n "$d" ] && [ -d "$d/platform-tools" ] && {
    export ANDROID_HOME="$d"
    break
  }
done

cat <<'EOF'

  HSM operations e2e
  ──────────────────
  Real access-mechanism OpaqueClient  ─▶  real wallet-client-gateway
      (X-API-KEY + challenge-response SESSION)  ─▶  wallet-bff / hsm-worker / SoftHSM

  Each test stands up a fresh device: new gateway account → challenge-response
  session → OPAQUE registration(pin), then re-authenticate(pin) before every
  mutating HSM call (the worker allows one per OPAQUE session).

    registers_authenticates_and_lists_the_auto_provisioned_key
        authenticate → listHsmKeys — a fresh device has >= 1 auto-provisioned P-256 key

    signs_raw_bytes_and_verifies_against_the_hsm_key
        sign(kid, bytes) → the P1363 signature verifies against the key's JWK

    app_style_jws_signed_via_raw_sign_verifies_against_the_hsm_key
        build "header.payload", sign() it, append the signature — exactly as
        wallet-app-android's JwtUtils.signJwtWith does — the compact JWS verifies

    creates_then_deletes_an_hsm_key
        createHsmKey → list (+1) → deleteHsmKey → list (gone)

  Gateway unreachable ⇒ tests skip (JUnit Assume), not fail.

EOF

cd "$here" || exit 1
run=""
command -v mise >/dev/null 2>&1 && run="mise exec --"
$run ./gradlew connectedCheck --stacktrace
rc=$?

xml="$(find build/outputs/androidTest-results -name '*.xml' -type f 2>/dev/null | sort | tail -1)"
if [ -n "$xml" ] && command -v python3 >/dev/null 2>&1; then
  python3 - "$xml" <<'PY'
import os, sys, xml.etree.ElementTree as ET


def first_line(node):
    msg = (node.get("message") or "").strip()
    if msg:
        return msg.splitlines()[0]
    body = (node.text or "").strip()
    return body.splitlines()[0] if body else ""


root = ET.parse(sys.argv[1]).getroot()
suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
counts = {"PASS": 0, "SKIP": 0, "FAIL": 0}
rows = []
for s in suites:
    for tc in s.iter("testcase"):
        node = tc.find("failure")
        if node is None:
            node = tc.find("error")
        skipped = tc.find("skipped")
        if node is not None:
            st, detail = "FAIL", first_line(node)
        elif skipped is not None:
            st, detail = "SKIP", first_line(skipped)
        else:
            st, detail = "PASS", ""
        counts[st] += 1
        rows.append((st, tc.get("name"), detail))

mark = {"PASS": "✓", "SKIP": "–", "FAIL": "✗"}
emoji = {"PASS": "✅", "SKIP": "➖", "FAIL": "❌"}
tally = f"{counts['PASS']} passed, {counts['SKIP']} skipped, {counts['FAIL']} failed"

print("  Result")
for st, name, detail in rows:
    print(f"    {mark[st]} {st}  {name}")
print(f"\n  {tally}\n")

# GitHub Actions job summary + failure annotations
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a", encoding="utf-8") as f:
        f.write(f"## HSM operations e2e\n\n**{tally}**\n\n")
        f.write("| | Test | Note |\n|---|---|---|\n")
        for st, name, detail in rows:
            note = detail.replace("|", "\\|")
            f.write(f"| {emoji[st]} | `{name}` | {note} |\n")
for st, name, detail in rows:
    if st == "FAIL":
        print(f"::error title=hsm-e2e::{name}: {detail}")
PY
elif [ -n "$xml" ]; then
  echo "  (install python3 for the summary table)"
else
  echo "  (no test results found under build/outputs/androidTest-results)"
  [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '## HSM operations e2e\n\n:warning: no test results produced\n' >>"$GITHUB_STEP_SUMMARY"
fi

exit "$rc"
