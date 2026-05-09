#!/usr/bin/env bash
# Build :app:assembleDebug on Termux / Proot aarch64 without Android Studio.
# Run from repo root:   ./tools/assemble-debug-on-termux.sh
# Or:                   bash tools/assemble-debug-on-termux.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck source=/dev/null
source "$ROOT/tools/bootstrap-termux-android-sdk.sh"

echo "sdk.dir=$ANDROID_HOME" >"$ROOT/local.properties"

ARCH="$(uname -m)"
GRADLE_ARGS=(--no-daemon)
if [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" || "$ARCH" == armv7* ]]; then
  # AGP's Maven AAPT2 is x86_64; force native binary from patched SDK build-tools.
  GRADLE_ARGS+=(-Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/34.0.0/aapt2")
fi

echo "Running ./gradlew ${GRADLE_ARGS[*]} :app:assembleDebug ..." >&2
./gradlew "${GRADLE_ARGS[@]}" :app:assembleDebug "$@"

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo "" >&2
  echo "OK: $APK" >&2
  ls -lh "$APK" >&2
fi
