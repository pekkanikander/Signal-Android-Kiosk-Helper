# QR provisioning snippet (dedicated device)

Use this JSON as the content of the QR code at the **Welcome** screen (*tap anywhere 6×* to open QR setup). Host the APK on your laptop (same Wi‑Fi) or a trusted local server.

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "fi.iki.pnr.kioskhelper/.AdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "http://<host>:8000/KioskHelper.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "<base64 sha-256 of signing cert>",
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
  "android.app.extra.PROVISIONING_WIFI_SSID": "<MyLabWiFi>",
  "android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE": "WPA",
  "android.app.extra.PROVISIONING_WIFI_PASSWORD": "<supersecret>",
  "android.app.extra.PROVISIONING_WIFI_HIDDEN": false
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
