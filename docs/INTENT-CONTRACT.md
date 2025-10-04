# Intent contract (ENABLE/DISABLE) — v0.1

All calls are **explicit** and must target `fi.iki.pnr.kioskhelper/.KioskCommandActivity`.
Exported components require the **signature permission** `fi.iki.pnr.kioskhelper.permission.KIOSK_CONTROL` and we validate the **caller signature**.

> **Fail‑fast:** if DND access is missing, the helper **does not** apply partial policy and returns `ERR_DND_PERMISSION_MISSING`.

---

## Actions
- `fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK`
- `fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK`

## Optional extras (caller → helper)
- `extra_allowlist: ArrayList<String>` — packages allowed in Lock Task
  *Default:* `["fi.iki.pnr.kioskhelper", "org.thoughtcrime.securesms"]`
- `extra_features: Int` — Lock Task features bitmask
  *Default:* omit NOTIFICATIONS; do not include HOME/RECENTS/GLOBAL_ACTIONS; include KEYGUARD
- `extra_suppress_status_bar: Boolean` — whether to call `setStatusBarDisabled(true)` outside Lock Task
  *Default:* `true`
- `extra_dnd_mode: String` — one of `none | alarms | total`
  *Default:* `total`; if DND access not granted → error (`ERR_DND_PERMISSION_MISSING`)
- `extra_result_receiver: android.os.ResultReceiver` — optional immediate result callback (see **Results**)

## Results
**Immediate result** (if `extra_result_receiver` is provided) and/or logcat. Values:
- `OK`
- `ERR_NOT_DEVICE_OWNER`
- `ERR_DND_PERMISSION_MISSING` *(no policy applied)*
- `ERR_INVALID_PARAMS`
- `ERR_INTERNAL`

**Atomic transition broadcasts** (sticky=false):
- `fi.iki.pnr.kioskhelper.KIOSK_APPLIED`
  Extras: `extra_dnd_active: Boolean` (expected `true` for v0.1)
- `fi.iki.pnr.kioskhelper.KIOSK_CLEARED`

> The caller (e.g., Signal) should **wait for the broadcast** before navigating into/out of Accessibility Mode.

---

## Security rules
- **Signature permission** required on the activity.
- **Reject implicit intents**: calls must include `setPackage("fi.iki.pnr.kioskhelper")`.
- **Verify caller**: compare the caller’s signing cert SHA‑256 against `@array/allowed_callers`. Mismatch → **no‑op**.

---

## Error semantics
- **DND missing** → return `ERR_DND_PERMISSION_MISSING` and **do nothing**.
- **Not Device Owner** → return `ERR_NOT_DEVICE_OWNER`.
- **Signal not installed** (or not in allowlist) → return `ERR_INVALID_PARAMS`.

---

## Example (Kotlin) — enable
```kotlin
val intent = Intent("fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK")
  .setClassName("fi.iki.pnr.kioskhelper", "fi.iki.pnr.kioskhelper.KioskCommandActivity")
  .putStringArrayListExtra("extra_allowlist", arrayListOf(
    "fi.iki.pnr.kioskhelper", "org.thoughtcrime.securesms"
  ))
  .putExtra("extra_suppress_status_bar", true)
  .putExtra("extra_dnd_mode", "total")

startActivity(intent)

// Then wait for broadcast: fi.iki.pnr.kioskhelper.KIOSK_APPLIED
```

## Example (Kotlin) — disable
```kotlin
val intent = Intent("fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK")
  .setClassName("fi.iki.pnr.kioskhelper", "fi.iki.pnr.kioskhelper.KioskCommandActivity")

startActivity(intent)

// Then wait for broadcast: fi.iki.pnr.kioskhelper.KIOSK_CLEARED
```

---
