# Headless DO + PO design (future, not implemented for v1.0)

Starting with Android 14 (API 34), many devices ship in **Headless System User** mode: user **0** runs system services in the background and an ordinary **foreground user** (for example, user 10) handles all UI. Android 14 also introduced a **Headless Device Owner – Affiliated** mode. In this model, a DPC installed as **Device Owner (DO)** on the headless system user is automatically accompanied by the same DPC acting as a **Profile Owner (PO)** in each *affiliated* foreground user. This is the recommended way to combine device‑wide policy with per‑user/UI control on headless devices.

**What this means for us:** Our helper APK can play **both roles**—DO in user 0 for global policy (status‑bar suppression, lock‑task allowlists, etc.) and **PO in the foreground user** for anything that must run in the UI user (launching Signal, entering Lock Task, reacting to intents). Both roles use the **same APK** but run as **separate processes** in different users with separate app data. No special cross‑user IPC is required for the MVP; Signal ⇄ helper communication happens **within** the foreground user where the PO instance lives.

## 1 Provisioning model (affiliated headless DO)

On devices that report headless mode (`UserManager.isHeadlessSystemUserMode()`), provisioning a DO is only allowed if the DPC declares support for **affiliated headless DO**. Practically, this means adding the headless declaration to our device‑admin metadata so the system will also establish a **PO** in each affiliated foreground user during provisioning.

**Device‑admin metadata (example):**

```xml
<!-- res/xml/device_admin.xml (snippet) -->
<device-admin xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-policies>
        <!-- minimal set for our helper (add as needed) -->
        <force-lock />
        <wipe-data />
    </uses-policies>

    <!-- Android 14+: Headless DO mode declaration -->
    <headless-system-user
        android:headless-device-owner-mode="affiliated" />
</device-admin>
```

> Notes:
> * On **non‑headless** devices (API 33/34 where `isHeadlessSystemUserMode()` is false), the declaration is ignored and standard DO behavior applies.
> * If the device is headless and the DPC **doesn’t** declare an allowed headless mode, DO provisioning is rejected by the platform.

### 2 Runtime responsibilities

- **DO (user 0):**
  - Persist device‑wide allowlists for Lock Task (`DevicePolicyManager.setLockTaskPackages`) and configure default **Lock Task features**.
  - Apply global restrictions (where supported) such as status‑bar disable outside Lock Task.
  - Enforce boot‑time policy checks (fail‑fast if prerequisites like DND access are missing; do **not** partially apply kiosk on boot).

- **PO (foreground user):**
  - Receive **ENABLE/DISABLE** intents from Signal (explicit, signature‑gated).
  - Resolve and **launch Signal** in the same user and start **Lock Task**.
  - Handle UI‑adjacent toggles (e.g., local DND behavior, per‑user notification listener if used as backstop).

> **No special IPC required:** We intentionally keep interactions **within the foreground user**. The DO and PO instances operate independently; we don’t rely on cross‑user Binder (reserved to system/signature).

### 3 Compatibility by API level

- **API 33 (Android 13):** Headless system user isn’t the default on phones; standard **DO‑only** works, and this section can be ignored.
- **API 34 (Android 14):** Headless system user and **Headless DO – Affiliated** are available. If the device is headless, declare `android:headless-device-owner-mode="affiliated"` to provision DO; the system installs the **PO** for the foreground user(s).
- **API 35 (Android 15):** Same model as API 34; continue to use the affiliated pattern on headless devices.

### 4 Pros and cons (vs DO‑only)

**Pros**
- Works on headless devices without hacks; UI actions run in the **correct user** (no package‑visibility issues).
- Still a **single helper APK**; the platform instantiates DO/PO roles as needed.
- Clear separation: DO handles global policy, PO handles user‑visible flows.

**Cons**
- **Provisioning complexity** is higher (affiliation requirement; verify PO presence in foreground user).
- Two processes (one per user) means separate preferences/state; keep state minimal and idempotent.

### 5 Migration plan (post‑v1)

1. Add the `<headless-system-user android:headless-device-owner-mode="affiliated"/>` declaration to `device_admin.xml`.
2. Extend the setup guide with a **headless checklist** (how to verify `isHeadlessSystemUserMode()`, confirm PO presence, and test in a foreground user).
3. Keep the Intent contract unchanged; on headless devices the **PO** instance handles UI actions; on non‑headless devices, DO‑only continues to work.

### 6 Further reading (canonical references)

- **Headless Device Owner & headless system user (Android 14+):**
  https://developer.android.com/work/device-admin
- **DeviceAdminInfo – headless DO API surface (added in API 34):**
  API diff: https://developer.android.com/sdk/api_diff/34/changes/android.app.admin.DeviceAdminInfo
- **AOSP multi‑user & headless system user background:**
  https://source.android.com/docs/devices/admin/multi-user
- **UserManager (headless mode notes in AOSP):**
  https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/UserManager.java
- **Android Enterprise overview – dedicated devices (COSU):**
  https://developers.google.com/android/work/overview
