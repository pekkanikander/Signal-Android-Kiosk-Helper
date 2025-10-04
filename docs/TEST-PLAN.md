# Test plan (v0.1 MVP)

## Device matrix
Test at least one device/emulator on each of:
- **API 28**, **29**, **30**, **33**, **34**

## Preconditions
- Device is provisioned with **Kiosk Helper** as **Device Owner**
- **DND access** granted to the helper (unless the scenario tests absence)
- **Signal** (our fork) installed as `org.thoughtcrime.securesms`

## Scenarios
1. **Enable success (happy path)**
   - Call `ACTION_ENABLE_KIOSK` with defaults.
   - Expect immediate `OK` and broadcast `KIOSK_APPLIED(extra_dnd_active=true)`.
   - Verify: Home returns to Signal; pulling shade blocked; no HUNs; sound/vibration silent.

2. **Enable fail — DND missing**
   - Revoke DND access.
   - Call `ACTION_ENABLE_KIOSK`.
   - Expect `ERR_DND_PERMISSION_MISSING`; **no policy applied** (status bar/notifications behave normally).

3. **Enable fail — not Device Owner**
   - Fresh device without DO, install helper.
   - Call `ACTION_ENABLE_KIOSK`.
   - Expect `ERR_NOT_DEVICE_OWNER`.

4. **Enable fail — Signal not installed**
   - Uninstall Signal.
   - Call `ACTION_ENABLE_KIOSK`.
   - Expect `ERR_INVALID_PARAMS`.

5. **Atomic navigation**
   - Caller waits for `KIOSK_APPLIED` before entering Accessibility Mode; waits for `KIOSK_CLEARED` before leaving.
   - Ensure no flicker or notifications during transition.

6. **HOME behavior**
   - With kiosk applied, press **Home** repeatedly → always lands in Signal.
   - Kill Signal (ADB) → HOME re-launches Signal.

7. **Status bar behavior**
   - While kiosk applied, attempt shade; confirm blocked (inside by features, outside by DPM setting).
   - After CLEAR, shade returns to normal.

8. **Reboot with preconditions satisfied**
   - With kiosk applied and DND granted, reboot.
   - Expect kiosk re-applied on boot. Verify behavior from #1.

9. **Reboot with DND revoked**
   - Revoke DND, reboot.
   - Expect **no-op** on boot (no partial policy). Calling ENABLE returns `ERR_DND_PERMISSION_MISSING`.

10. **Restore HOME**
    - Clear kiosk.
    - Expect previous launcher restored. If prior component missing, Android HOME picker appears once.

11. **Safe mode**
    - Boot to Safe mode: third-party apps disabled.
    - Reboot normally: helper returns; DND grant persists; kiosk can be re-enabled.

12. **Signature gate**
    - Attempt ENABLE from an untrusted test app.
    - Expect helper to **no-op** (permission/signature validation).

13. **R8/shrinker sanity**
    - Build release with minify.
    - Ensure receivers/admin survive and app still functions.

## Manual regression checklist
- No network permissions in manifest.
- App size small; no unexpected deps.
- Battery impact negligible (no long-running services).
- Logs contain clear results/errors, no stacktraces in happy path.

---

# QR provisioning snippet (dedicated device)

Use this JSON as the content of the QR code at the **Welcome** screen (*tap anywhere 6×* to open QR setup). Host the APK on your laptop (same Wi‑Fi) or a trusted local server.

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "fi.iki.pnr.kioskhelper/.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "http://<host>:8000/KioskHelper.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "<base64 sha-256 of signing cert>",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true
}
```

## Steps
1. On your laptop, serve the APK locally:
   ```bash
   cd /path/to/apk && python3 -m http.server 8000
   ```
2. Generate a QR from the JSON above (the **raw JSON text** is the QR payload).
3. On the phone’s **Welcome** screen: tap 6× → choose *Scan QR code* → connect to the same Wi‑Fi → scan.
4. Follow prompts; the device installs the helper and sets it as **Device Owner**.
5. Finish setup, then grant **DND access** to the helper once.

## Getting the signature checksum
For production, include `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM`.

- With Android build tools:
  ```bash
  apksigner verify --print-certs KioskHelper.apk
  # Copy the "Signer #1 certificate SHA-256 digest" (hex) and convert to base64.
  ```
- Example conversion (Python):
  ```python
  import binascii, base64
  hex_digest = "<paste-hex>".replace(":","",
).lower()
  print(base64.b64encode(binascii.unhexlify(hex_digest)).decode())
  ```

> During development you may omit the checksum; for production, include it to prevent tampering.

---
