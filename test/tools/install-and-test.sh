#!/usr/bin/env bash
set -euo pipefail

SIGNAL_PKG="org.thoughtcrime.securesms"
SIGNAL_DATA_TAR="tmp/signal-data.tar"
mkdir -p "$(dirname "$SIGNAL_DATA_TAR")"

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)

adb wait-for-device

# 0) Install helper + Signal (fresh device assumptions live in install.sh)
bash "$SCRIPT_DIR/install.sh" "$@"

# 2) If no saved state yet, launch once for provisioning and capture
echo "Launching Signal for provisioning…"
adb shell am start --user 0 -n org.thoughtcrime.securesms/.RoutingActivity
echo "Once provisioning is complete, press ENTER to continue…"
read -r

echo "Stopping Signal…"
adb shell am force-stop "$SIGNAL_PKG"

# 4) Run the kiosk test flow
bash "$SCRIPT_DIR/test.sh"

echo "Done."
