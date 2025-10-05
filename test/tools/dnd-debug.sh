#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

# ------------------------------------------------------------------
# DND (Do Not Disturb) access debugging helper for ADB (Android 13+)
# ------------------------------------------------------------------
# What this script does
#  - Shows Android version + device basics
#  - Detects the *foreground* user id (handles headless system user)
#  - Verifies the app declares ACCESS_NOTIFICATION_POLICY
#  - Optionally grants DND policy access via `cmd notification allow_dnd`
#  - Prints multiple views that may indicate access state:
#       • Service view (only if the OS supports a list subcommand)
#       • Secure setting (legacy; often null on modern Android)
#       • AppOps snapshot (indicative; not authoritative)
#
# Notes for Android 14/15 (API 34/35):
#  - There is *no* public shell subcommand to list DND-approved packages.
#    The secure setting is often null even when access is granted.
#  - The most reliable check is inside the app with
#      NotificationManager.isNotificationPolicyAccessGranted().
# ------------------------------------------------------------------

PKG="fi.iki.pnr.kioskhelper"   # default package to inspect
GRANT=false
USER_ID=""                    # empty → auto-detect foreground user
ALL_USERS=false

usage() {
  cat <<EOF
Usage: $0 [--pkg PACKAGE] [--user ID | --all-users] [--grant]

Options:
  --pkg PACKAGE    Package to inspect (default: ${PKG})
  --user ID        Inspect/grant for a specific Android user id
  --all-users      Inspect all users on the device (no grant)
  --grant          Attempt to grant DND access for the target user
  -h, --help       Show this help

Examples:
  $0                          # inspect current user for ${PKG}
  $0 --pkg org.example.app    # inspect another package
  $0 --grant                  # try auto-grant for current user
  $0 --all-users              # list status hints for every user
EOF
}

# --- arg parse ---------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --pkg) PKG="$2"; shift 2 ;;
    --user) USER_ID="$2"; shift 2 ;;
    --all-users) ALL_USERS=true; shift ;;
    --grant) GRANT=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

# --- small utils -------------------------------------------------------------
need() { command -v "$1" >/dev/null || { echo "Missing: $1"; exit 2; }; }
adbsh() { adb shell "$@"; }

color() { tput setaf "$1" 2>/dev/null || true; }
resetc() { tput sgr0 2>/dev/null || true; }

hr() { printf '%s\n' "------------------------------------------------------------"; }

cur_user() {
  local uid
  # Preferred (Android 10+):
  uid=$(adbsh cmd activity get-current-user 2>/dev/null | tr -cd '0-9' || true)
  if [[ -z "$uid" ]]; then
    # Fallback (older builds / wrappers):
    uid=$(adbsh am get-current-user 2>/dev/null | tr -cd '0-9' || true)
  fi
  if [[ -z "$uid" ]]; then
    # Last resort: parse dumpsys user for "current" flag
    uid=$(adbsh dumpsys user 2>/dev/null | awk '/UserInfo/ && /current/ {print $2; exit}')
  fi
  [[ -n "$uid" ]] || uid=0
  echo "$uid"
}

list_users() {
  adbsh dumpsys user 2>/dev/null | awk '/UserInfo/ {gsub(/[{}]/," "); print $2, $0}'
}

has_manifest_perm() {
  adbsh dumpsys package "$PKG" 2>/dev/null | grep -q "android.permission.ACCESS_NOTIFICATION_POLICY" || return 1
}

notif_usage() {
  adbsh cmd notification -h 2>/dev/null || true
}

supports_service_list() {
  # Only call list subcommand if the OS advertises it in help text.
  notif_usage | grep -q "get_notification_policy_access"
}

service_list_for_user() {
  local u="$1"
  if supports_service_list; then
    adbsh cmd notification get_notification_policy_access --user "$u" 2>/dev/null || true
  else
    echo "<not available on this Android version>"
  fi
}

settings_list_for_user() {
  local u="$1"
  adbsh settings --user "$u" get secure enabled_notification_policy_access_packages 2>/dev/null || true
}

appops_view() {
  # AppOps is only indicative. Some builds show ACCESS_NOTIFICATION_POLICY when used.
  adbsh appops get "$PKG" 2>/dev/null | grep -i "ACCESS_NOTIFICATION_POLICY" || true
}

try_grant_user() {
  local u="$1"
  echo "Attempting: adb shell cmd notification allow_dnd $PKG $u"
  if adbsh cmd notification allow_dnd "$PKG" "$u" >/dev/null 2>&1; then
    echo "Grant command reported success for user $u."
    return 0
  fi
  echo "Grant command not supported or failed on this build/user ($u)."
  return 1
}

# --- main --------------------------------------------------------------------
need adb

hr
echo "Device info:"
adb devices
ANDROID_REL=$(adbsh getprop ro.build.version.release 2>/dev/null || true)
ANDROID_SDK=$(adbsh getprop ro.build.version.sdk 2>/dev/null || true)
echo "ro.build.version.release = ${ANDROID_REL}"
echo "ro.build.version.sdk     = ${ANDROID_SDK}"
hr

if ! has_manifest_perm; then
  echo "$(color 3)WARN$(resetc): Package '$PKG' does NOT declare android.permission.ACCESS_NOTIFICATION_POLICY (per dumpsys)."
  echo "       The system will not grant DND policy access to packages that don't request it."
else
  echo "$(color 2)OK$(resetc): Package declares ACCESS_NOTIFICATION_POLICY."
fi

hr
if $ALL_USERS; then
  echo "Users on device:"; list_users || true; hr
  echo "DND indicators by user (service/settings/appops):"
  # Pull candidate user ids from dumpsys user
  for u in $(adbsh dumpsys user 2>/dev/null | awk '/UserInfo/ {print $2}' || true); do
    echo "User $u:"
    printf "  service : %s\n" "$(service_list_for_user "$u" | tr '\n' ' ' | sed 's/  */ /g')"
    printf "  settings: %s\n" "$(settings_list_for_user "$u")"
    printf "  appops  : %s\n" "$(appops_view | sed 's/^/           /')"
    echo
  done
  exit 0
fi

U="${USER_ID:-$(cur_user)}"
echo "Inspecting user: $U"

# Helpful warning for headless system user setups
if [[ "$U" != "0" ]]; then
  echo "Note: Many emulators/devices run a headless system user (0) and a foreground user like 10."
  echo "      Granting for user 0 won't help if the foreground user is $U."
fi

if $GRANT; then
  try_grant_user "$U" || true
fi

SERVICE_VIEW=$(service_list_for_user "$U")
SECURE_VIEW=$(settings_list_for_user "$U")
APPOPS_VIEW=$(appops_view)

echo "Service view (list support varies by Android version):"
printf "  %s\n" "${SERVICE_VIEW:-<none>}"

echo "Secure setting (legacy, often null on modern Android):"
printf "  %s\n" "${SECURE_VIEW:-<none>}"

echo "AppOps (indicative only):"
if [[ -n "$APPOPS_VIEW" ]]; then
  printf "  %s\n" "$APPOPS_VIEW"
else
  echo "  <no ACCESS_NOTIFICATION_POLICY record>"
fi

hr
# Interpretation (best-effort):
if supports_service_list && echo "$SERVICE_VIEW" | tr ':' '\n' | grep -qx "$PKG"; then
  echo "$(color 2)DND ACCESS: CONFIRMED GRANTED$(resetc) for '$PKG' on user $U (service list)."
  exit 0
fi

if [[ -n "$APPOPS_VIEW" ]] && echo "$APPOPS_VIEW" | grep -qi "allow"; then
  echo "$(color 3)DND ACCESS: LIKELY GRANTED$(resetc) for '$PKG' on user $U (based on AppOps; not authoritative)."
  echo "Tip: Verify inside the app with NotificationManager.isNotificationPolicyAccessGranted()."
  exit 0
fi

# If we get here, we couldn't confirm from shell-side views.
echo "$(color 1)DND ACCESS: NOT CONFIRMED$(resetc) for '$PKG' on user $U."
echo "Hints:"
echo "  • On Android 14/15, there is no shell command to list approved packages." \
     "Use an in-app check (NotificationManager.isNotificationPolicyAccessGranted)."
echo "  • Make sure you're targeting the *foreground* user (try: adb shell cmd activity get-current-user)."
echo "  • If you just granted access, re-open Settings → Notifications → Do Not Disturb → Apps that can control DND"

echo "Exit code does not imply denial; only that we couldn't confirm via shell on this build."
exit 2
