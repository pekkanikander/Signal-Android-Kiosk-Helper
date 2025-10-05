#!/usr/bin/env bash
set -euo pipefail

HELPER_PKG="fi.iki.pnr.kioskhelper"
SIGNAL_PKG="org.thoughtcrime.securesms"

# Namespaced extras (canonical)
EXTRA_ALLOWLIST="${HELPER_PKG}.extra.ALLOWLIST"
EXTRA_SUPPRESS_STATUS_BAR="${HELPER_PKG}.extra.SUPPRESS_STATUS_BAR"
EXTRA_DND_MODE="${HELPER_PKG}.extra.DND_MODE"


# ENABLE kiosk (namespaced extras only)
echo "Sending ENABLE intent…"
adb shell am start -n ${HELPER_PKG}/.KioskCommandActivity \
  -a ${HELPER_PKG}.ACTION_ENABLE_KIOSK \
  --es "${EXTRA_DND_MODE}" total \
  --ez "${EXTRA_SUPPRESS_STATUS_BAR}" true \
  --esa "${EXTRA_ALLOWLIST}" ${HELPER_PKG} ${SIGNAL_PKG}

sleep 10

# DISABLE kiosk
echo "Sending DISABLE intent…"
adb shell am start -n ${HELPER_PKG}/.KioskCommandActivity \
  -a ${HELPER_PKG}.ACTION_DISABLE_KIOSK
