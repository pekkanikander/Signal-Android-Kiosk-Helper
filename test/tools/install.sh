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
HELPER_PKG="fi.iki.pnr.kioskhelper"

SIGNAL_APK="../Signal-Android-Accessibility-Kiosk/app/build/outputs/apk/playProd/debug/Signal-Android-play-prod-arm64-v8a-debug-7.56.4.apk"
SIGNAL_PKG="org.thoughtcrime.securesms"

ADMIN_RCVER="${HELPER_PKG}/.AdminReceiver"

IME_APK="../simple-keyboard/app/build/outputs/apk/accessibility/debug/app-accessibility-debug.apk"
IME_ID="rkr.simplekeyboard.inputmethod/.latin.LatinIME"

DISABLE_GBOARD=true

usage() {
  cat <<EOF
Usage: $0 --signal-apk PATH [--helper-apk PATH]

Options:
  --signal-apk PATH   Path to Signal APK to install (required)
  --helper-apk PATH   Path to Kiosk Helper APK (default: ${HELPER_APK_DEFAULT})
  --ime-apk PATH      Path to a third-party IME APK to sideload (optional)
  --ime-id NAME       IME id to enable & set (e.g. rkr.simplekeyboard.inputmethod/.latin.LatinIME)
  -h, --help          Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --signal-apk)     SIGNAL_APK="$2"; shift 2 ;;
    --helper-apk)     HELPER_APK="$2"; shift 2 ;;
    --ime-apk)        IME_APK="$2"; shift 2 ;;
    --ime-id)         IME_ID="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

[[ -n "$SIGNAL_APK" ]] || { echo "--signal-apk is required"; usage; exit 2; }
[[ -f "$SIGNAL_APK" ]] || { echo "Signal APK not found: $SIGNAL_APK"; exit 2; }
[[ -f "$HELPER_APK" ]] || { echo "Helper APK not found: $HELPER_APK"; exit 2; }

command -v adb >/dev/null || { echo "adb not found on PATH"; exit 2; }

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

echo "Enable on-screen keyboard (for emulator)"
adb shell settings put secure show_ime_with_hard_keyboard 1

# --- Optional: install + select a minimal IME ---
if [[ -n "$IME_APK" ]]; then
  echo "Installing IME for user 0: $IME_APK"
  install_user0 "$IME_APK"
  echo "Launching IME app once to register/prepare"
  IME_PKG="${IME_ID%%/*}"
  # Try to open the launcher activity; fall back to App Info if no launcher.
  #   adb shell monkey -p "$IME_PKG" -c android.intent.category.LAUNCHER
  adb shell am start --user 0 -n rkr.simplekeyboard.inputmethod/.latin.settings.SettingsActivity
  echo "Press ENTER when you've completed any on-screen enable steps…"
  read -r
  echo "Configuring IME: $IME_ID"
  echo "  ime enable $IME_ID"; adb shell ime enable "$IME_ID"
  echo "  ime set $IME_ID"; adb shell ime set "$IME_ID"
fi

# Disable voice typing IMEs (best effort)
adb shell ime list -s | grep -i voice | while read -r VID; do
  echo "  ime disable $VID"; adb shell ime disable "$VID"
done

if $DISABLE_GBOARD; then
  echo "Disabling Gboard (com.google.android.inputmethod.latin) for user 0…"
  adb shell pm disable-user --user 0 com.google.android.inputmethod.latin || true
fi

# --- 4) Determine current user and grant DND (always) ---
USER_ID=$(adb shell cmd activity get-current-user | tr -cd '0-9')
if [[ -z "$USER_ID" ]]; then USER_ID=0; fi

echo "Granting DND policy access to $HELPER_PKG for user $USER_ID…"
adb shell cmd notification allow_dnd "$HELPER_PKG" "$USER_ID"

# Tip: If the hardware keyboard still doesn't type, ensure Emulator → Settings → General →
# "Enable keyboard input" is ON and cold boot the AVD. You can still paste (Cmd+V) or use
# adb input as a fallback during provisioning.
