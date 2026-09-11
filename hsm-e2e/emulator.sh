#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Digg - Agency for Digital Government
#
# SPDX-License-Identifier: CC0-1.0

# Headless Android emulator helper for the hsm-e2e test.
#
#   emulator.sh boot [avd]   boot a headless emulator (falls back to any AVD, or
#                            creates a host-arch one; API from ANDROID_API, default 34)
#   emulator.sh stop         kill any running emulator
#
# Finds the SDK from ANDROID_HOME / ANDROID_SDK_ROOT / the Linux/macOS default.

set -euo pipefail

API="${ANDROID_API:-34}"
AVD="${2:-hsm-e2e}"

find_sdk() {
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" \
    "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/AppData/Local/Android/Sdk"; do
    [ -n "$d" ] && [ -d "$d/platform-tools" ] && {
      echo "$d"
      return
    }
  done
  echo "Android SDK not found. Set ANDROID_HOME (looked in the platform defaults too)." >&2
  exit 1
}

SDK="$(find_sdk)"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"

cmdline_bin() {
  for d in "$SDK/cmdline-tools/latest/bin" "$SDK/cmdline-tools"/*/bin "$SDK/tools/bin"; do
    [ -x "$d/sdkmanager" ] && {
      echo "$d"
      return
    }
  done
  echo ""
}

host_abi() {
  case "$(uname -m)" in
  arm64 | aarch64) echo "arm64-v8a" ;;
  *) echo "x86_64" ;;
  esac
}

ensure_avd() {
  local avds bin img requested="$AVD"
  avds="$("$EMU" -list-avds 2>/dev/null || true)"

  echo "$avds" | grep -qx "$AVD" && return

  # fall back to any existing AVD before touching the SDK
  if [ -n "$avds" ]; then
    AVD="$(echo "$avds" | head -1)"
    echo "AVD '$requested' not found — using existing AVD '$AVD'"
    return
  fi

  bin="$(cmdline_bin)"
  [ -n "$bin" ] || {
    echo "No AVDs found and no cmdline-tools to create one." >&2
    echo "In Android Studio install 'SDK Command-line Tools (latest)', or run:" >&2
    echo "  sdkmanager 'system-images;android-${API};google_apis;$(host_abi)'" >&2
    echo "  avdmanager create avd -n '$requested' -k 'system-images;android-${API};google_apis;$(host_abi)'" >&2
    exit 1
  }
  img="system-images;android-${API};google_apis;$(host_abi)"
  echo "creating AVD '$AVD' ($img)..."
  { yes || true; } | "$bin/sdkmanager" --licenses >/dev/null 2>&1 || true
  { yes || true; } | "$bin/sdkmanager" "$img" >/dev/null # forgive `yes` SIGPIPE, keep its code
  printf 'no\n' | "$bin/avdmanager" create avd --force -n "$AVD" -k "$img"
}

# Citrix App Protection preloads a lib that segfaults the emulator — detect and stop.
citrix_guard() {
  grep -qi 'AppProtection' /etc/ld.so.preload 2>/dev/null || return 0
  cat >&2 <<'EOF'
Citrix App Protection is active — it segfaults the Android emulator.
Comment its line out of /etc/ld.so.preload for the run, e.g.:

  sudo sed -i.bak -E 's|^([^#].*AppProtection.*)|#\1|' /etc/ld.so.preload
  #  ... just hsm-test ...
  sudo mv /etc/ld.so.preload.bak /etc/ld.so.preload

then re-run.
EOF
  exit 1
}

boot() {
  [ -x "$ADB" ] || {
    echo "adb not found at $ADB" >&2
    exit 1
  }
  if "$ADB" devices | grep -qE '^emulator-[0-9]+[[:space:]]+device$'; then
    echo "emulator already running: $("$ADB" devices | grep -oE '^emulator-[0-9]+' | head -1)"
    return
  fi
  ensure_avd
  citrix_guard

  local log="${TMPDIR:-/tmp}/hsm-emulator.log"
  echo "booting '$AVD' (headless)... log: $log"
  nohup "$EMU" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot -no-metrics \
    -gpu swiftshader_indirect >"$log" 2>&1 &
  local pid=$!

  "$ADB" wait-for-device
  local deadline=$(($(date +%s) + 300))
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    kill -0 "$pid" 2>/dev/null || {
      echo "emulator process died — see $log" >&2
      exit 1
    }
    [ "$(date +%s)" -lt "$deadline" ] || {
      echo "emulator boot timed out — see $log" >&2
      exit 1
    }
    sleep 3
  done
  "$ADB" shell input keyevent 82 >/dev/null 2>&1 || true
  echo "emulator ready."
}

stop() {
  [ -x "$ADB" ] || return 0
  for s in $("$ADB" devices | grep -oE '^emulator-[0-9]+'); do
    "$ADB" -s "$s" emu kill && echo "stopped $s"
  done
}

case "${1:-boot}" in
boot) boot ;;
stop) stop ;;
*)
  echo "usage: $0 {boot [avd]|stop}" >&2
  exit 2
  ;;
esac
