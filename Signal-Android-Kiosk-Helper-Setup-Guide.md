# Signal Android Kiosk Helper — Setup Guide (Caregiver)

**Version:** 0.1
**Supports:** Android 9 (API 28) and newer
**Audience:** Caregivers / assisting users setting up a dedicated device for the assisted user

> **What this does**
> This guide walks you through preparing a *fresh* Android device to run Signal in a simplified, distraction‑free **Kiosk Mode** using the **Kiosk Helper** app. While kiosk is ON, the device will: (1) hide notification UI, (2) silence sound/vibration, and (3) keep Signal in the foreground. You can turn kiosk OFF at any time.

> **Scope:** Kiosk is **active only while Signal’s Accessibility Mode is ON**. Outside Accessibility Mode, the device behaves normally.

---

## 0) Quick Checklist
- A device you can **factory reset** (all data is erased)
- Android **9+** (API 28+) — earlier versions are **not supported**
- The two APK files: **Kiosk Helper** and **Signal (Accessibility build)**
- Optional but recommended: a laptop with **ADB** installed
- A Wi‑Fi network you control (local network is fine; internet access is not required)

---

## 1) Understand the Roles (two apps)
- **Signal (Accessibility build):** where you enable/disable kiosk and choose options.
- **Kiosk Helper (separate APK):** a tiny, auditable app that controls the operating system features needed for kiosk. It becomes the device’s **Device Owner** and (temporarily) the **Launcher/Home** while kiosk is ON.

> **Device Owner (DO)** is an Android role required for true kiosk mode. Setting DO **requires a factory reset** and a one‑time provisioning step during initial setup.

---

## 2) Factory Reset the Device
1. Back up anything important (this process erases everything).
2. Open **Settings → System → Reset options → Erase all data (factory reset)**.
3. Wait for the device to reboot to the **Welcome** screen.

> You can do all steps with the device **offline**. For QR provisioning (below) you’ll need local Wi‑Fi to fetch the Kiosk Helper APK from your laptop or trusted server.

---

## 3) Install Kiosk Helper as **Device Owner**
There are two supported methods. Pick **A (QR)** unless you specifically need **B (ADB test)**.

### A) **QR Provisioning (Recommended)**
This uses the Android setup wizard’s built‑in *Scan QR code* flow for dedicated devices.

**A.1 Prepare a local download URL**
- Host the **Kiosk Helper APK** on your laptop (same Wi‑Fi as the phone). Example (macOS/Linux):
  ```bash
  cd /path/to/apk
  python3 -m http.server 8000
  ```
  Your APK URL will be: `http://<your-laptop-ip>:8000/KioskHelper.apk`

**A.2 Create provisioning JSON** (save as `provisioning.json` on your laptop)
```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.yourorg.kioskhelper/.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "<optional: base64 sha-256 of signing cert>",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "http://<your-laptop-ip>:8000/KioskHelper.apk",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true
}
```
> The checksum line is optional during development; for production, supply it to prevent tampering.

**A.3 Convert JSON to a QR code**
- Use any offline QR generator (on your laptop). The QR content is **the JSON text above**.

**A.4 Start QR provisioning on the device**
1. At the **Welcome** screen, tap anywhere **6 times** to open *QR code setup*.
2. Connect to your local Wi‑Fi (same network as the laptop).
3. Scan the QR code you created. The device will download and install the Kiosk Helper and make it **Device Owner**. Follow prompts.

**A.5 Finish basic setup**
- Proceed through the setup wizard (you can skip Google account, etc.).

> If the device won’t enter QR mode, some OEMs require tapping a small area or using *Start → Long‑press both volume keys*. If needed, use method **B**.

### B) **ADB Device‑Owner (Test/Developer Only)**
This is convenient for development units but not for production fleets.

1. Complete setup minimally, enable **Developer options** and **USB debugging**.
2. Install the Kiosk Helper APK via ADB:
   ```bash
   adb install KioskHelper.apk
   ```
3. Set Device Owner (requires a **freshly reset** device with no existing accounts/passes):
   ```bash
   adb shell dpm set-device-owner com.yourorg.kioskhelper/.AdminReceiver
   ```
4. Reboot the device.

If step 3 fails with “**not allowed**”, the device isn’t in a state that allows DO assignment — factory reset and try again or use **QR**.

---

## 4) Grant **Do Not Disturb** (DND) Access (once)
To guarantee silence (no sounds/vibration), the helper needs DND control.

1. Open **Settings → Notifications → Do Not Disturb**
2. Find **Apps that can control Do Not Disturb** (wording varies by OEM)
3. Enable access for **Kiosk Helper**

> If the menu is hard to find, open the helper (or Signal) and tap **Grant DND Access**, which opens the correct system screen.

---

## 5) Install Signal (Accessibility Build)
- Install the APK normally (sideload) or from your trusted source.
- Open it once and complete initial Signal setup (registration, permissions, etc.).

---

## 6) Enable Kiosk from Signal
1. In Signal, open **Settings → Accessibility Mode**.
2. Turn on **Use Kiosk Helper** (API 28+ only).
> If enabling fails with a message about **Do Not Disturb access**, complete Section 4 to grant DND access and try again.
3. Signal sends an **ENABLE** request to the helper.
4. The helper will:
   - Save your current Launcher as “previous HOME”
   - Apply kiosk policy (lock task, notification suppression, optional status‑bar disable)
   - Switch itself to be the **HOME** app
   - Launch Signal and keep it in the foreground

**Verification checklist**
- Press **Home** → returns to Signal.
- Pull down the **status bar** → should not open (or shows nothing relevant).
- Send a test notification from another app → no banner (HUN) or sound/vibration.

---

## 7) Disable Kiosk (from Signal)
1. In **Settings → Accessibility Mode**, turn **Use Kiosk Helper** **OFF**.
2. The helper will:
   - Exit lock task
   - Restore the **previous HOME/Launcher**
   - Restore DND/Status‑bar settings to prior values

> If you don’t see your old launcher immediately, press **Home** once. If Android asks to “Complete action using…”, choose your preferred launcher and **Always**.

---

## 8) Emergency Exit (Caregiver‑only)
If Signal becomes unresponsive while kiosk is active:
- On the (invisible) Helper **Home** screen, perform the secret gesture: **tap upper‑left corner 5 times**, then enter your **PIN**.
- Tap **Exit Kiosk**. The helper clears policy and restores the previous launcher.

> Configure the **PIN** the first time you enable kiosk. Keep it confidential.

---

## 9) Troubleshooting

**Enabling fails / DND required**
- The helper will not apply kiosk without DND access. Grant DND in **Section 4** and retry.

**Kiosk didn’t come back after reboot**
- Ensure **DND access** is still granted (Section 4). The helper only reapplies kiosk after boot if all preconditions are satisfied.

**Need to service the device / bypass temporarily**
- You can reboot into **Safe mode** to temporarily disable third‑party apps (including the helper), then reboot normally to restore kiosk capability.

**“This device is not provisioned / not Device Owner”**
- You must complete **Section 3**. If using ADB, ensure factory reset was done *just before* `dpm set-device-owner`.

**QR scan succeeds but APK won’t download**
- Ensure the phone and laptop are on the **same Wi‑Fi**.
- Use the phone’s browser to open `http://<laptop-ip>:8000/` to confirm reachability.

**Notifications still appear or device vibrates**
- Confirm **DND access** is granted to the helper (Section 4).
- Re‑enable kiosk to reapply policy. Some OEMs need both **kiosk policy** and **DND**.

**Can’t restore previous launcher**
- In **Settings → Apps → Default apps → Home app**, pick your launcher.
- Or disable/clear defaults for Kiosk Helper, then select your launcher when prompted.

**Forgot the emergency PIN**
- Factory reset the device to recover. (Data will be erased.)

**API < 28 message**
- The helper intentionally **refuses** to run kiosk on Android 8.x or lower. Upgrade the device.

---

## 10) Uninstall / Clean Removal
1. **Disable kiosk** from Signal (Section 7).
2. In **Settings → Apps → Kiosk Helper**, uninstall.
3. If uninstall is blocked (because it’s Device Owner), you must **factory reset** the device to remove Device Owner status.

---

## 11) Privacy & Security Notes
- The helper has **no network permissions** and never “calls home”.
- It stores only minimal policy state (no message content).
- The Device Owner role is powerful; keep the **APK signed** and sourced from your trusted build.

---

## 12) FAQ
**Q: Can I use this without factory reset?**
A: Not for full kiosk. Device Owner requires a fresh provisioning flow. Without DO you can only get partial suppression.

**Q: Does this block phone calls or alarms?**
A: If you select **DND: Total Silence**, *all* sounds/vibration are silenced. You can change to “Alarms only” in Signal’s kiosk options before enabling.

**Q: Can I allow another app (e.g., a dialer) in kiosk?**
A: Yes. In Signal’s kiosk options, add the app to the **allowlist** before enabling kiosk.

**Q: Will system updates break kiosk?**
A: After major updates, kiosk should reapply on reboot. If behavior changes, disable then re‑enable kiosk.

---

## 13) Appendix — Sample Values to Keep Handy
- **Helper component:** `com.yourorg.kioskhelper/.AdminReceiver`
- **Provisioning JSON keys:**
  - `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME`
  - `android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`
  - `android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM` (recommended)
  - `android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED`
  - `android.app.extra.PROVISIONING_SKIP_ENCRYPTION`

---

### You’re done!
The device is now ready to run Signal in a calm, distraction‑free Kiosk Mode. If anything is unclear in this guide, annotate it and we’ll iterate.

---
