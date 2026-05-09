#!/usr/bin/env bash
# Bootstrap Android SDK pieces on Termux / Proot aarch64 without Android Studio.
# Uses Google's command-line tools (Java sdkmanager runs on ARM) + lzhiyong static
# aarch64 build-tools/platform-tools to replace Google's x86_64 binaries.
#
# Prereqs (Termux):  pkg install openjdk-17 curl unzip
# Prereqs (Debian): apt install openjdk-17-jdk curl unzip
#
# Environment:
#   ANDROID_HOME  (default: $HOME/android-sdk)

set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME

CMDLINE_TOOLS_ZIP="commandlinetools-linux-14742923_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"

LZHIYONG_TAG="35.0.2"
ARCH="$(uname -m)"
case "$ARCH" in
  aarch64) LZHIYONG_ZIP="android-sdk-tools-static-aarch64.zip" ;;
  armv7l|armv8l|arm) LZHIYONG_ZIP="android-sdk-tools-static-arm.zip" ;;
  x86_64|amd64)
    echo "INFO: host is x86_64 — use normal sdkmanager / Android Studio; lzhiyong overlay skipped." >&2
    LZHIYONG_ZIP=""
    ;;
  *)
    echo "ERROR: unsupported uname -m: $ARCH (expected aarch64 or x86_64)" >&2
    exit 1
    ;;
esac

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: java not on PATH. Install openjdk-17 (Termux: pkg install openjdk-17)." >&2
  exit 1
fi

mkdir -p "$ANDROID_HOME"

install_cmdline_tools() {
  local tmp
  tmp="$(mktemp -d)"
  echo "Downloading Google command-line tools (sdkmanager is Java; OK on ARM)..." >&2
  curl -fSL --retry 3 -o "$tmp/tools.zip" "$CMDLINE_TOOLS_URL"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$ANDROID_HOME/cmdline-tools" "$tmp/unz"
  unzip -q "$tmp/tools.zip" -d "$tmp/unz"
  if [[ -d "$tmp/unz/cmdline-tools" ]]; then
    mv "$tmp/unz/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  else
    echo "ERROR: unexpected layout in command-line tools zip" >&2
    rm -rf "$tmp"
    exit 1
  fi
  rm -rf "$tmp"
}

if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
  install_cmdline_tools
fi

SDKMANAGER=("$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME")

echo "Accepting SDK licenses..." >&2
yes | "${SDKMANAGER[@]}" --licenses >/dev/null || true

echo "Installing platform android-34 and build-tools 34.0.0..." >&2
yes | "${SDKMANAGER[@]}" "platforms;android-34" "build-tools;34.0.0" >/dev/null || true

BT="$ANDROID_HOME/build-tools/34.0.0"
if [[ ! -d "$BT" ]]; then
  echo "ERROR: build-tools/34.0.0 missing after sdkmanager" >&2
  exit 1
fi

if [[ -n "$LZHIYONG_ZIP" ]]; then
  if [[ -x "$BT/aapt2" ]] && file "$BT/aapt2" 2>/dev/null | grep -q aarch64; then
    echo "INFO: $BT/aapt2 already aarch64 — skipping lzhiyong re-download." >&2
  else
    LZ_URL="https://github.com/lzhiyong/android-sdk-tools/releases/download/${LZHIYONG_TAG}/${LZHIYONG_ZIP}"
    echo "Downloading lzhiyong static tools (${LZHIYONG_ZIP})..." >&2
    lzh="/tmp/${LZHIYONG_ZIP}"
    curl -fSL --retry 3 -o "$lzh" "$LZ_URL"
    ex="$(mktemp -d)"
    unzip -q "$lzh" -d "$ex"
    echo "Overlaying native aapt2 / aidl / zipalign into $BT ..." >&2
    cp -f "$ex/build-tools/"* "$BT/" || true
    mkdir -p "$ANDROID_HOME/platform-tools"
    cp -f "$ex/platform-tools/"* "$ANDROID_HOME/platform-tools/" 2>/dev/null || true
    rm -rf "$ex"
  fi
fi

echo "ANDROID_HOME=$ANDROID_HOME" >&2
file "$BT/aapt2" >&2 || true
