#!/usr/bin/env bash
set -euo pipefail

# ------------------------------------------------------------
# Signal-Android-Kiosk-Helper — minimal install & test (API 33+)
# Assumptions (always true in this script):
#   • Fresh, factory-reset device/emulator (no accounts, no prior owner/profile)
#   • We will set Device Owner (DO)
#   • We will grant DND policy access
#   • Both APKs installed for **user 0**
#   • Namespaced intent extras only
# ------------------------------------------------------------
# Usage:
#   test/tools/install-and-test.sh --signal-apk /path/to/Signal.apk [--helper-apk /path/to/Helper.apk]
# Defaults:
#   HELPER_APK defaults to app/build/outputs/apk/debug/app-debug.apk
# ------------------------------------------------------------

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd -- "${SCRIPT_DIR}/../../" && pwd)

HELPER_APK_DEFAULT="${REPO_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
HELPER_APK="${HELPER_APK_DEFAULT}"
SIGNAL_APK=""

HELPER_PKG="fi.iki.pnr.kioskhelper"
ADMIN_RCVER="${HELPER_PKG}/.AdminReceiver"
SIGNAL_PKG="org.thoughtcrime.securesms"

# Namespaced extras (canonical)
EXTRA_ALLOWLIST="${HELPER_PKG}.extra.ALLOWLIST"
EXTRA_SUPPRESS_STATUS_BAR="${HELPER_PKG}.extra.SUPPRESS_STATUS_BAR"
EXTRA_DND_MODE="${HELPER_PKG}.extra.DND_MODE"

usage() {
  cat <<EOF
Usage: $0 --signal-apk PATH [--helper-apk PATH]

Options:
  --signal-apk PATH   Path to Signal APK to install (required)
  --helper-apk PATH   Path to Kiosk Helper APK (default: ${HELPER_APK_DEFAULT})
  -h, --help          Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --signal-apk) SIGNAL_APK="$2"; shift 2 ;;
    --helper-apk) HELPER_APK="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

[[ -n "$SIGNAL_APK" ]] || { echo "--signal-apk is required"; usage; exit 2; }
[[ -f "$SIGNAL_APK" ]] || { echo "Signal APK not found: $SIGNAL_APK"; exit 2; }
[[ -f "$HELPER_APK" ]] || { echo "Helper APK not found: $HELPER_APK"; exit 2; }

command -v adb >/dev/null || { echo "adb not found on PATH"; exit 2; }

echo "Waiting for device…"
adb wait-for-device

# Install an APK for **user 0** (factory-fresh device)
install_user0() {
  local apk_path="$1"; local remote="/data/local/tmp/$(basename "$apk_path")"
  echo "  push $(basename "$apk_path") → $remote"; adb push "$apk_path" "$remote"
  echo "  pm install -r --user 0 $remote"; adb shell pm install -r --user 0 "$remote"
  echo "  rm $remote"; adb shell rm -f "$remote"
}

echo "Installing helper for user 0: $HELPER_APK"
install_user0 "$HELPER_APK"

echo "Setting Device Owner: $ADMIN_RCVER"
adb shell dpm set-device-owner "$ADMIN_RCVER"

echo "Installing Signal for user 0: $SIGNAL_APK"
install_user0 "$SIGNAL_APK"

# --- 4) Determine current user and grant DND (always) ---
USER_ID=$(adb shell cmd activity get-current-user | tr -cd '0-9')
if [[ -z "$USER_ID" ]]; then USER_ID=0; fi

echo "Granting DND policy access to $HELPER_PKG for user $USER_ID…"
adb shell cmd notification allow_dnd "$HELPER_PKG" "$USER_ID"

# --- 5) ENABLE kiosk (namespaced extras only) ---
echo "Sending ENABLE intent…"
adb shell am start -n ${HELPER_PKG}/.KioskCommandActivity \
  -a ${HELPER_PKG}.ACTION_ENABLE_KIOSK \
  --es "${EXTRA_DND_MODE}" total \
  --ez "${EXTRA_SUPPRESS_STATUS_BAR}" true \
  --esa "${EXTRA_ALLOWLIST}" ${HELPER_PKG} ${SIGNAL_PKG}

sleep 2

# --- 6) DISABLE kiosk ---
echo "Sending DISABLE intent…"
adb shell am start -n ${HELPER_PKG}/.KioskCommandActivity \
  -a ${HELPER_PKG}.ACTION_DISABLE_KIOSK

echo "Done."
