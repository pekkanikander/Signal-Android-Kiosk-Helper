# Signal‑Android‑Kiosk‑Helper — Implementation Spec (v0.1)

**Goal:** enable Cursor to generate the **smallest possible** MVP APK offline, matching the design.

## 1) Canonical identifiers (use exactly these)
- **Package:** `fi.iki.pnr.kioskhelper`
- **App label:** `Kiosk Helper`
- **Signature‑permission (exported components must require this):**
  - Name: `fi.iki.pnr.kioskhelper.permission.KIOSK_CONTROL`
  - Protection level: `signature`
  - Label/Description: `Control kiosk policy`
- **Intent actions (explicit, setPackage+setClass):**
  - `fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK`
  - `fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK`
- **Broadcasts (atomic transitions):**
  - `fi.iki.pnr.kioskhelper.KIOSK_APPLIED`
  - `fi.iki.pnr.kioskhelper.KIOSK_CLEARED`
- **Result extras (for callback/broadcast):**
  - `extra_result`: `OK | ERR_NOT_DEVICE_OWNER | ERR_DND_PERMISSION_MISSING | ERR_INVALID_PARAMS | ERR_INTERNAL`
  - `extra_message`: optional human‑readable message
  - `extra_dnd_active`: `boolean` (present on `KIOSK_APPLIED`)

## 2) Components to generate

### 2.1 `AdminReceiver` (extends `DeviceAdminReceiver`)
- No UI. Implements minimal overrides; logs events.

### 2.2 `HomeActivity` (minimal launcher)
- Intent filters: `MAIN + HOME + DEFAULT`.
- Theme: transparent, no UI, `singleInstance`, `excludeFromRecents=true`.
- On `onResume()`:
  - If kiosk policy **applied**: ensure Signal activity is running/foreground; otherwise `startActivity(signalIntent)`.
  - If **not applied**: immediately `finish()` to hand control back (outside kiosk mode).

### 2.3 `KioskCommandActivity`
- Transparent, `singleTop`. **Exported=true**, **permission**=`KIOSK_CONTROL`.
- Handles **ENABLE/DISABLE** actions.
- On ENABLE flow:
  1) **Prechecks**: API≥28; device owner?; `NotificationManager.isNotificationPolicyAccessGranted()`.
  2) If any fail → send result `ERR_*` and `finish()`.
  3) Apply policy via `KioskController` (below), then broadcast `KIOSK_APPLIED` (+ `extra_dnd_active=true`) and `finish()`.
- On DISABLE flow: undo policy, broadcast `KIOSK_CLEARED`, `finish()`.

### 2.4 `KioskController` (plain Kotlin singleton or small Service)
- Holds **idempotent** methods:
  - `applyKiosk(admin: ComponentName, allowlist: List<String>, features: Int, suppressStatusBar: Boolean)`
  - `clearKiosk(admin: ComponentName)`
- Persists: previous HOME `ComponentName`, prior DND level, last features/allowlist.
- **No long‑running work**; do not keep a foreground service.

### 2.5 `BootReceiver`
- `RECEIVE_BOOT_COMPLETED`.
- On boot: **fail‑fast** — only re‑apply if all preconditions still hold (DO, DND). Else **no‑op**.

## 3) Policy API to call (exact)
- **Allowlist + features:**
  - `dpm.setLockTaskPackages(admin, arrayOf("fi.iki.pnr.kioskhelper", "org.thoughtcrime.securesms"))`
  - `dpm.setLockTaskFeatures(admin, <bitmask>)` (defaults omit notifications; see §5)
- **Become HOME (apply):** persistent preferred activity for `HomeActivity` via `DevicePolicyManager.addPersistentPreferredActivity(...)`.
- **Restore HOME (clear):** if stored component exists → `addPersistentPreferredActivity` to that; else `clearPackagePersistentPreferredActivities(...)`.
- **Start lock task:** launch Signal using `ActivityOptions.makeBasic().setLockTaskEnabled(true)` and `startActivity()`.
- **Status bar (outside lock task):** `dpm.setStatusBarDisabled(admin, suppressStatusBar)`.
- **DND check:** `NotificationManager.isNotificationPolicyAccessGranted` (no DND toggle here if missing).

## 4) Defaults (baked‑in for v0.1)
- **Min/target/compile SDK:** min=28, target=34, compile=34 (OK to bump compile/target locally).
- **Lock Task Features:**
  - Omit `LOCK_TASK_FEATURE_NOTIFICATIONS`
  - Do **not** include `LOCK_TASK_FEATURE_HOME`
  - Do **not** include `LOCK_TASK_FEATURE_RECENTS`
  - Do **not** include `LOCK_TASK_FEATURE_GLOBAL_ACTIONS`
  - **Include** `LOCK_TASK_FEATURE_KEYGUARD`
- **Allowlist:** helper + Signal package.
- **Suppress status bar:** true.
- **DND mode:** `total` (must be granted; otherwise `ERR_DND_PERMISSION_MISSING`).

## 5) Data keys for Intent extras (optional)
- `extra_allowlist: ArrayList<String>`
- `extra_features: Int`
- `extra_suppress_status_bar: Boolean`
- `extra_dnd_mode: String` (`none|alarms|total`) — currently **must** be `total` or grant present
- `extra_result_receiver: android.os.ResultReceiver` (optional)

## 6) Caller validation
- **Require** `KIOSK_CONTROL` permission on exported components.
- Additionally verify caller signature:
  1) From `Binder.getCallingUid()` → packages → `PackageManager.getPackageInfo(..., GET_SIGNING_CERTIFICATES)`
  2) Compare SHA‑256 cert digest against a **resource array** `allowed_callers`
  3) If mismatch → log + **no‑op**

## 7) Minimal storage schema (SharedPreferences)
- `previous_home.flattened_component` (String)
- `last_features` (Int)
- `last_allowlist` (Set<String>)
- `last_dnd_level` (String)
- `kiosk_applied` (Boolean)

## 8) Edge‑case behavior
- If Signal not installed when enabling → `ERR_INVALID_PARAMS`.
- If device not DO → `ERR_NOT_DEVICE_OWNER`.
- If API < 28 → same error.
- On clear: always attempt to restore HOME; if component missing → clear helper’s persistent preferred to show chooser.

## 9) Non‑functional
- **No network permissions**.
- **No foreground service**.
- Keep app size small: no libraries beyond AndroidX core/appcompat if needed.
- ProGuard/R8: keep `DeviceAdminReceiver`, `BootReceiver`, and `HomeActivity`.

---
