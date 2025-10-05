# Signal Android Kiosk Helper — Design Document

**Status:** Draft

**Audience:** Signal Accessibility Mode maintainers, Android/Kiosk integrators, QA

**Scope:** Design for a *separate*, minimal, auditable APK that provides Device‑Owner‑backed kiosk controls for Signal‑Android’s Accessibility Mode.

---

## 1) Background & Intent

### 1.1 Why

We are building an **Accessibility Mode** for Signal‑Android, targeted at cognitively challenged users (small children, elderly with dementia, etc.). The goal is **zero surprises**:
- No visual popups, heads‑up notifications (HUNs), icons, bubbles, banners, lock‑screen intrusions
- No haptics, LEDs, or sounds from background apps
- Stay focused on a *single conversation* with large, simple controls

While Signal can suppress its *own* notifications, full suppression requires **OS‑level control**.
We aim to keep the Signal baseline pristine and implement OS policy in a **separate helper APK** that can be reviewed independently.

### 1.2 What kind of kiosk

We want a **dedicated‑device kiosk** that:
- Runs on **Android 13 / API 33+** only (earlier versions: fail fast with a clear error)
  - Prior to API 28 would require changes to Signal-Android.
  - Limiting to 33+ keeps the implementation small, aligning with Android 11+ package visibility rules,
    more consistent **Lock Task / notification** behavior, and our deployment model (factory-reset, curated devices).
    Supporting 28–32 would add branching and QA load with little benefit for our target users.
- Uses **Device Owner (DO)** privileges (provisioned on a *freshly reset* device)
- Exposes a tiny **Intent API** that Signal can call to **apply** and **clear** kiosk policy
- Acts as the **HOME/Launcher** while kiosk is active to guarantee safe recovery and auto‑restart
- Operates **offline** (no analytics, no network, no “phone‑home”)

We will use DND (“Total silence”) in addition to notification UI suppression for belt‑and‑suspenders.
> **DND = Do Not Disturb**: a system mode that silences sounds and vibration.

---

## 2) Goals & Non‑Goals

### Goals
- True **system‑level suppression** of notification UI, sound, and vibration while Accessibility Mode is on
- **Minimal** changes to Signal baseline (Signal only toggles kiosk features via an Intent)
- **Auditable** helper: small codebase, clear responsibilities, no network
- **Deterministic deployment** with a clear, step‑by‑step provisioning guide

### Non‑Goals
- BYOD support without Device Owner
- Remote fleet management / server MDM
- Complex multi‑app kiosks beyond an explicit, fixed allowlist

---

## 3) Constraints & Assumptions

- **Android version:** API **33+** only; older versions return an error and do nothing.
  - **Rationale:** Avoids legacy notification/DND edge cases and pre-Android-11 package-visibility differences;
    matches current Lock Task behavior and minimizes OEM divergence.
- **Ownership:** Device must be provisioned with our helper as **Device Owner (DO)** — requires **factory reset** (acceptable for our use case).
- **Package visibility:** enforced per-user since Android 11. Signal and the helper must be installed in the same user (DO (v1) or PO (later))
- **Network:** No internet connectivity required or used by the helper.
- **Permissions:** Device admin (DO), optional DND access, boot completed. No root required.
- **User model:** A caregiver/assisting user performs the setup once; the assisted user uses the kiosk daily.

---

## 4) High‑Level Architecture

**Components (helper APK):**
1. **AdminReceiver** — standard `DeviceAdminReceiver` to receive DO callbacks and expose policy APIs.
2. **KioskController** *(Service)* — applies/clears policy based on incoming Intents; persists and restores state.
3. **HomeActivity (Minimal Launcher)** — declared as `HOME|DEFAULT`; visually blank/neutral; ensures there is always a “safe surface” to relaunch Signal; can start/stop lock task.
4. **BootReceiver** — on reboot, re‑applies policy **only if all preconditions are met** (incl. DND granted). If any precondition is missing (e.g., DND not granted), it **fails fast and no‑ops**; Signal remains in normal mode until the caregiver re‑enables kiosk from Signal.
5. **(Optional) NotificationBackstop** — `NotificationListenerService` that cancels any non‑allow‑listed notifications in case of OEM quirks (after explicit user/MDM grant).

**External:** Signal‑Android (our Accessibility-Kiosk fork) calls the helper via explicit **Intents** when Accessibility Mode Kiosk-features are toggled.

---

## 5) Policy Surface (what the helper toggles, a minimal initial design)

When **APPLY kiosk**:
- **Lock Task** on (dedicated device):
  - Allowlist packages (helper + Signal, others optional)
  - Configure **Lock Task Features** and **omit notifications** (i.e., do **not** include the notifications feature)
- **Disable Status Bar** (outside of lock task) to block shade/quick settings when relevant
- **DND: Total Silence** (if DND access granted) to suppress all sound/haptics
  - Now targeting API 33+. On newer targets there may be a need to migrate to a rule-based DND
- **User restrictions (initially mandatory, later optional via the intent):**
  - Do allow safe boot and factory reset; requires knowledge that the assisted user is unlikely to have
  - Add other relevant restrictions if needed for the deployment
- **Launcher role:** set helper as **persistent HOME** so Home always returns to a controlled surface
- **Start Signal** in lock task and keep it in foreground; if Signal exits, HOME resumes and relaunches it

### 5.1 Lock Task Feature defaults (API 33+)

| Feature constant (DevicePolicyManager)        | Default | Rationale |
|---|---|---|
| `LOCK_TASK_FEATURE_NOTIFICATIONS`            | **Omitted** | Hides notification icons/shade/HUNs while in lock task. |
| `LOCK_TASK_FEATURE_HOME`                     | **Not included** | Prevents jumping to other launchers. |
| `LOCK_TASK_FEATURE_RECENTS`                  | **Not included** | Prevents task switching. |
| `LOCK_TASK_FEATURE_GLOBAL_ACTIONS`           | **Not included** | Hides long‑press power menu to reduce confusion. |
| `LOCK_TASK_FEATURE_KEYGUARD`                 | **Included (default)** | Allows normal sleep/lock behavior. |
| `LOCK_TASK_FEATURE_SYSTEM_INFO`              | **Not included** | Avoids status info UI surfacing during kiosk. |

> These are conservative defaults for v1. They can be overridden via the Intent extras in later iterations but the helper will ship with these baked‑in defaults.

---

## 6) State Machine (helper‑internal)

```
UNPROVISIONED  ->  PROVISIONING  ->  READY_IDLE  ->  APPLIED  ->  CLEARING -> READY_IDLE
     ^                                                              |
     └──────────────────────────────(factory reset required)────────┘
```

- **UNPROVISIONED:** Helper not device owner. Any APPLY request returns an error with guidance.
- **PROVISIONING:** Performed once on factory‑fresh device. Sets helper as DO.
- **READY_IDLE:** DO active but no kiosk policy applied.
- **APPLIED:** Kiosk policy applied; helper is HOME; Signal running in lock task.
- **CLEARING:** Rolling back policy to READY_IDLE.

Persisted fields: previous HOME component, last DND level, last applied features/allowlist, timestamps.

---

## 7) Intent Contract (Signal ⇄ Helper)

All Intents are **explicit** (package + class) and gated by a **signature permission** and/or caller verification.

### Actions
- `fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK`
- `fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK`

### Common extras (all optional unless stated)
- `fi.iki.pnr.kioskhelper.extra.ALLOWLIST: String[]` — packages permitted in lock task (default: helper + Signal)
- `fi.iki.pnr.kioskhelper.extra.FEATURES: Int` — Lock Task Features bitmask (with **notifications omitted** by default)
- `fi.iki.pnr.kioskhelper.extra.SUPPRESS_STATUS_BAR: Boolean` — default `true`
- `fi.iki.pnr.kioskhelper.extra.DND_MODE: String` — `none | alarms | total` (default `total` if access granted)
- `fi.iki.pnr.kioskhelper.extra.RESULT_RECEIVER: android.os.ResultReceiver` — optional callback for status

> **Legacy aliases accepted for v0.x:** `allowlist`, `features`, `suppressStatusBar`, `dndMode`, `resultReceiver`/`resultPendingIntent`. If both namespaced and legacy keys are present, the helper uses the namespaced values.

### Result codes (returned via callback or sticky local broadcast)
- `OK`
- `ERR_NOT_DEVICE_OWNER`
- `ERR_DND_PERMISSION_MISSING` *(no policy applied, the caller should retry with dndMode = none)*
- `ERR_INVALID_PARAMS`
- `ERR_INTERNAL`

> Note: We **do not** depend on foreground Activity results. The helper can finish immediately after scheduling work.

---

## 8) Provisioning & Deployment (caregiver‑facing)

**Prerequisites:**
- Target device can be **factory reset**
- Helper APK and Signal APK available (local sideload)
- A computer with `adb` (optional but recommended)

Note that Signal and helper must be installed for the same Android user (per-user package visibility on Android 11+).

**One‑time steps per device:**
1. **Factory reset** the device. During initial setup, keep the device offline if desired.
2. **Install & set the helper as Device Owner (DO).** This must happen during/after initial setup using a supported method:
   - **QR / NFC / Zero‑touch** managed provisioning; or
   - **ADB (test only)**: use the standard `set-device-owner` flow (only on debug‑build helper and non‑production devices).
3. **Grant DND access** to the helper (once), so it can switch to **Total Silence** while kiosk is active.
4. **Install Signal** (our fork).

**To enable Kiosk mode (atomic transition):**
5. In Signal, open **Accessibility Mode** settings and toggle **“Use Kiosk Helper”**.
6. Signal sends **ENABLE** (explicit Intent) and **waits** for the helper to broadcast `fi.iki.pnr.kioskhelper.KIOSK_APPLIED`.
7. The helper:
   - Saves the current launcher as “previous HOME”
   - Applies policy (lock task, features, status bar, DND) — if DND missing, it **fails** with `ERR_DND_PERMISSION_MISSING`
   - Sets itself as HOME and **relaunches Signal**
8. After receiving `KIOSK_APPLIED`, Signal navigates into Accessibility Mode.
9. **Verification:** press Home, pull the shade, send notifications — nothing disruptive should appear; sounds/vibration silent.

**Reboot behavior (v1, fail‑fast):** After a reboot, the helper re‑applies kiosk **only if** DND access is still granted and other preconditions hold. If DND was revoked or is missing, the helper does **not** partially apply kiosk; it does nothing and waits for Signal to request ENABLE again.

**Disabling kiosk (atomic transition):**
1. When leaving Accessibility Mode (e.g., returning to Settings), Signal sends **DISABLE** and waits for `fi.iki.pnr.kioskhelper.KIOSK_CLEARED`.
2. The helper exits lock task, restores prior HOME/launcher and DND/status‑bar settings, then launches Signal to the **Settings** screen.
3. After receiving `KIOSK_CLEARED`, Signal completes the navigation.

> Design principle: **kiosk is active only while Accessibility Mode is active**. Outside Accessibility Mode, the device behaves normally.

That is, the Kiosk mode is on only when in Accessibility Mode (if toggled on).  When existing Accessibility Mode, the Kiosk mode is turned off.  This allows the assisting user to exit the Accessibility Mode to Signal settings, then use the Home and other buttons to go e.g. to Android Settings, etc. If restoring the previous HOME fails (component missing), the helper will call `clearPackagePersistentPreferredActivities()` to let Android show the HOME picker once.


---

## 9) Security & Privacy
- **No network permissions** — helper cannot exfiltrate data.
- **Signature‑level permission** on exported components; additionally validate the caller’s UID/package allowlist.
- **Minimal persistence:** only policy state (booleans, bitmasks), previous HOME component, timestamps. No user content.
- **Auditability:** codebase is small; single purpose; no trackers.

**Intent hardening**
- Exported components require a **signature‑level** permission unique to the helper.
- **Reject implicit intents**: all calls must include `setPackage(...)` targeting the helper.
- Verify caller identity: check `Binder.getCallingUid()` → packages → verify the **signing certificate** matches the installed Signal build.
- Log and no‑op on any mismatch.

---

## 10) UX & Interop with Signal
- Signal shows a **single toggle**: *“Enable Kiosk (requires helper)”* with a status line: *Installed / Not installed / Not provisioned*.
- When enabled, Signal sends **ENABLE** and waits for `KIOSK_APPLIED` before navigating into Accessibility Mode. When exiting, it sends **DISABLE** and waits for `KIOSK_CLEARED` before returning to Settings. **Transitions are atomic.**
- If helper absent or unprovisioned, "Enable Kiosk" cannot be chosen, with a link to the caregiver guide underneath.

---

## 11) Testing Strategy
- **Unit tests (helper):** state machine transitions; persistence/restore; parameter validation.
- **Instrumented tests:** apply/clear flows on API 33, 34, 35; reboot recovery; DND permission granted/denied branches.
- Boot with DND revoked → helper no‑ops (no partial apply); enabling from Signal returns `ERR_DND_PERMISSION_MISSING`.

---

## 12) Maintainability & Upstreaming Posture
- Signal baseline remains minimal; the helper is **optional**. Without the helper, Accessibility Mode still works (no kiosk).
- All OS‑level behavior is isolated in the helper; future changes to Android kiosk APIs affect only this small APK.
- Clear version gating at **API 33+** simplifies support.

---

## 13) Alternatives Considered
- **Only DND + Notification Listener (no DO):** simpler but cannot suppress all visual UI reliably.
- **DPC without being HOME:** avoids HOME switching but loses the “safe surface” and robust relaunch behavior.
- **Full MDM / remote provisioning:** out of scope (privacy, complexity).
- **Root‑based solutions:** unnecessary and undesirable.

## 14) Headless Device Owner – Affiliated aka DO + PO design (future, not implemented for v1.0)

Starting with Android 14 (API 34), many devices ship in **Headless System User** mode:
user **0** runs system services in the background and an ordinary **foreground user** (e.g. user 10) handles all UI.
Android 14 also introduced a **Headless Device Owner – Affiliated** mode.
In this model, a DPC installed as **Device Owner (DO)** on the headless system user is automatically accompanied
by the same DPC acting as a **Profile Owner (PO)** in each *affiliated* foreground user.
This is the recommended way to combine device‑wide policy with per‑user/UI control on headless devices.

Hence, our plan is that this helper APK will play **both roles**—DO in user 0 for global policy (status‑bar suppression,
lock‑task allowlists, etc.) and **PO in the foreground user** for anything that must run in the UI user (launching Signal,
entering Lock Task, reacting to intents).
Both roles will use the **same APK** but run as **separate processes** in different users with separate app data.

### Pros and cons vs Device Owner only

For v1.0, we will implement the Device Owner only model.
Compared to this DO-only mode, the DO + PO model has the following pros and cons.

**Pros**
- Works on headless devices without hacks; UI actions run in the **correct user** (no package‑visibility issues).
- Still a **single helper APK**; the platform instantiates DO/PO roles as needed.
- Clear separation: DO handles global policy, PO handles user‑visible flows.

**Cons**
- **Provisioning complexity** is higher (affiliation requirement; verify PO presence in foreground user).
- Two processes (one per user) means separate preferences/state; keep state minimal and idempotent.
- Requires coordination between the two processes

See the dedicated write-up: **[Headless DO + PO design](./Signal-Android-Kiosk-Helper-Design-DO+PO.md)**.

## 15) Open Questions & Risks
- Exact **Lock Task Features** set per OS version (e.g., behavior differences on Android 14/15)
- OEM deviations in status‑bar disabling and DND semantics
- Accessibility of the **emergency exit gesture** (discoverable to caregiver, not to assisted user)
- **Android 14+ headless system user**: DO lives on user 0; verify APPLY/CLEAR across reboots and any user switches.
- **Boot‑time behavior (resolved for v1):** if DND access is missing, the helper **fails fast and no‑ops** (no partial policy, no boot‑time broadcast). Signal handles remediation when the caregiver reenables kiosk.

Mitigations: conservative defaults; explicit OS‑version gating; caregiver documentation; backstop Notification Listener.

## 16) Caregiver Documentation (outline)
1. What this mode does
2. Devices and Android versions supported (Android 13+ only)
3. **Factory reset is required** (why and how)
4. Install helper and make it Device Owner (QR/ADB walkthrough)
5. Grant DND access
6. Install Signal
7. Enable/disable kiosk from Signal
8. Emergency exit (PIN gesture)
9. Troubleshooting FAQ (common errors & resolutions)

---

## 17) Glossary
- **Device Owner (DO):** Special admin app that manages a dedicated device. Required for full kiosk controls.
- **Lock Task:** Android capability to keep apps in the foreground and restrict system UI.
- **Lock Task Features:** Fine‑grained toggles controlling which system features remain available during Lock Task.
- **DND (Do Not Disturb):** System mode to silence sounds and vibration; we use **Total Silence**.
- **HUN:** Heads‑Up Notification, a banner shown over the current app.

---

## 18) Change Log
- v0.1: initial design for headless helper DPC+Launcher (was API 28+), Intent contract, provisioning plan, and caregiver guide outline.
- v0.1 (this doc): bumps minimum API level to 33+, add notes about DO + PO or Headless Device Owner – Affiliated design

---

**Linkage:** This helper implements “Phase 4.3: Basic kiosk mode” in the project plan and keeps Signal’s upstream delta minimal by externalizing OS‑level controls.

---
