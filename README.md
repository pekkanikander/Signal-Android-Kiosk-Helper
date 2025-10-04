# Signal‑Android‑Kiosk‑Helper

> A tiny, auditable **Device Owner + minimal Launcher** that gives Signal‑Android (mainly its [Accessibility Mode fork](../../../Signal-Android-Accessibility-Kiosk/)) a true, OS‑level **kiosk** on Android **9+** — with **no network**, **no analytics**, and **fail‑fast** behavior.

<p align="left">
  <a href="#status"><img alt="Status" src="https://img.shields.io/badge/status-alpha-orange" /></a>
  <a href="#platform"><img alt="Android" src="https://img.shields.io/badge/Android-API_28%2B-blue" /></a>
  <a><img alt="Device Owner" src="https://img.shields.io/badge/Device%20Owner-required-red" /></a>
  <a><img alt="Network" src="https://img.shields.io/badge/network-none-brightgreen" /></a>
  <a href="#license"><img alt="License" src="https://img.shields.io/badge/license-Unlicense-blue" /></a>
</p>

## TL;DR
- Runs as **Device Owner (DO)** on a **factory‑fresh** Android device (API **28+**).
- Acts as a **minimal launcher (HOME)** and applies **Lock Task** with notifications suppressed, plus **Do Not Disturb (DND)** → **Total Silence**.
- Exposes a **tiny Intent API** that your app (e.g., our Signal‑Android fork) calls to **ENABLE** or **DISABLE** kiosk.
- **Fail‑fast**: if DND access is missing, the helper **does nothing** and returns an error; it never applies partial policy.
- **No network permissions**, no phone‑home, small codebase → easy to review.

> Primary audience: engineers familiar with Signal‑Android who want to understand how and why this helper exists.
> Secondary audience: Signal maintainers reviewing upstream PRs from our **Signal‑Android‑Accessibility‑Kiosk** repo.

---

## Why this exists
Signal can reliably silence **its own** notifications, but avoiding **all** distractions (HUNs, status‑bar icons/shade, sounds, vibration) for cognitively challenged users requires **OS‑level controls**. We aim to keep our changes to Signal’s baseline minimal and therefore have put kiosk policy in a **separate APK** that can be audited independently and that is optional at runtime.

- Project sibling: **Signal‑Android‑Accessibility‑Kiosk** (our Signal fork). It **can** run without this helper (no kiosk).
- This helper can be used by **other apps** too — the Intent API is generic.

---

## Features (v0.1 design)
- **Dedicated‑device kiosk** (DO required); **Launcher/HOME** role while active
- **Lock Task** with **notifications omitted** (no icons/shade/HUNs)
- Optional **status‑bar disable** outside Lock Task
- **DND: Total Silence** (requires one‑time DND grant)
- **Atomic transitions**: app waits for `KIOSK_APPLIED`/`KIOSK_CLEARED` broadcasts
- **Persistent HOME restore** with fallback to the Android HOME picker if needed
- **No network**; **signature‑permission** and **explicit intents** only

---

## Platform & prerequisites
- **Android**: 9+ (API **28+**). Earlier versions are **not supported**.
- **Ownership**: device must be provisioned with this helper as **Device Owner** (requires **factory reset**).
- **DND access**: user must grant **Do Not Disturb** access once to the helper. If not granted, enabling kiosk **fails fast**.

---

## Quick start
1. **Read** the design and setup guides:
   - [Design: Signal‑Android‑Kiosk‑Helper‑Design.md](./Signal-Android-Kiosk-Helper-Design.md)
   - [Setup: Signal‑Android‑Kiosk‑Helper‑Setup‑Guide.md](./Signal-Android-Kiosk-Helper-Setup-Guide.md)
2. **Provision** as Device Owner (QR provisioning recommended). Factory reset is required.
3. **Grant DND access** to the helper once (system Special app access).
4. **Install your app** (e.g., our Signal fork) and call the Intent API below.

> This repository ships without a server, analytics, or remote provisioning. Everything is **offline**.

---

## Intent API (app ↔ helper)
All intents are **explicit** (set package/class). Exported components require a **signature‑level** permission; we also verify the **caller signature**.

### Actions
- `fi.iki.pnr.kioskhelper.ACTION_ENABLE_KIOSK`
- `fi.iki.pnr.kioskhelper.ACTION_DISABLE_KIOSK`

### Extras (optional unless noted)
- `allowlist: String[]` — packages allowed in Lock Task (default: helper + caller)
- `features: Int` — Lock Task features (defaults omit notifications)
- `suppressStatusBar: Boolean` — default `true`
- `dndMode: String` — `none | alarms | total` (default `total`; if DND not granted → **error**)
- `suppressErrorDialogs: Boolean` — default `true`
- `resultReceiver` or `resultPendingIntent` — optional for immediate result

### Results (via callback/broadcast)
- `OK`
- `ERR_NOT_DEVICE_OWNER`
- `ERR_DND_PERMISSION_MISSING` *(no policy applied; caller should prompt to grant DND or retry with `dndMode=none` if supported)*
- `ERR_INVALID_PARAMS`
- `ERR_INTERNAL`

### Broadcasts (atomic transitions)
- `fi.iki.pnr.kioskhelper.KIOSK_APPLIED`
- `fi.iki.pnr.kioskhelper.KIOSK_CLEARED`

---

## Using with our Signal fork
Repository: **Signal‑Android‑Accessibility‑Kiosk** (lives alongside this repo).

- In **Accessibility Mode** settings, users can toggle “Use Kiosk Helper”.
- The fork calls `ACTION_ENABLE_KIOSK`, waits for `KIOSK_APPLIED`, then navigates into the simplified conversation UI.
- On exit it calls `ACTION_DISABLE_KIOSK`, waits for `KIOSK_CLEARED`, then returns to Settings.
- If DND access is missing, the fork surfaces a clear **how to grant DND** prompt and does not attempt partial kiosk.

This keeps Signal’s upstream delta small; the helper is optional at runtime.

---

## Security & privacy
- **No network permissions**; APK cannot call home.
- **Signature‑permission** on exported components; **explicit intents only**; verify caller **signing cert**.
- Stores only minimal policy state (previous HOME, flags). **No user content**.
- **Safe mode** remains available for caregiver recovery.

Threat model (v0.1): accidental distractions are prevented; **malicious local users** are **out of scope**.

---

## Limitations
- Dedicated devices only (Device Owner). **Factory reset** is required to install.
- **API 28+** only.
- Requires **DND** grant to enable kiosk.
- Multi‑user/managed‑profile configurations are not supported in v1.

---

## Roadmap
- **v0.1** — Design + caregiver setup guide (this repo)
- **v0.2** — MVP helper APK: DO provisioning, HOME, Lock Task, DND fail‑fast, atomic broadcasts
- **v0.3** — QA hardening across API 28–34; OEM sanity checks; tests
- **v1.0** — First public release
- Later: optional notification listener backstop; allowlist UI

---

## Contributing
Issues and PRs welcome. Please keep the helper **small and auditable**:
- No networking/analytics deps
- Avoid new runtime permissions
- Document any policy change in the design doc and setup guide

For security topics, open a private channel if possible.

---

## License
This project is released under **The Unlicense** (public domain).

The companion Signal fork (**Signal‑Android‑Accessibility‑Kiosk**) remains under its upstream license (**AGPLv3**).

---

## Acknowledgements
- Android COSU/Device Owner patterns and Lock Task APIs
- The broader Signal‑Android community

---

## Status
This project is **alpha** and evolving. Expect breaking changes before v1.0.

---
