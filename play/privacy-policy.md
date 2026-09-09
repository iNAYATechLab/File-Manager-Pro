# Privacy Policy — File Manager Pro

_Last updated: 2026-09-09_

File Manager Pro ("the app") is a local file manager. This policy explains what
information the app handles. Short version: **everything stays on your device** —
the app does not collect, transmit, or sell personal data.

## 1. Data handled by the app

- **Files and folders you browse and manage** are accessed on your device only.
  Operations (copy, move, delete, compress, extract, vault) run locally.
- **Vault**: files you add to a vault are encrypted with AES-256-GCM and stored
  inside a `.fmpvault` folder on your device. The password is derived locally
  and is never sent anywhere. Biometric unlock uses the Android Keystore; the
  credential material never leaves the device.
- **Settings and preferences** (sort order, view mode, language, etc.) are stored
  in local app preferences on your device.
- **SAF locations** you grant (e.g. `Android/data`) are remembered only as
  system content URIs so the app can reopen them; permissions can be revoked
  any time in the app ("Protected folders") or in system settings.

## 2. Permissions used

- **All files access / storage** — required for a file manager to list and
  manage files across storage volumes.
- **Biometric (fingerprint)** — optional; used only to unlock your vault
  locally. No biometric data is stored by the app.

## 3. Network, analytics, ads

The app has **no analytics SDKs, no ad SDKs, no account system**, and does not
contact any server for its core features. Optional in-app actions (e.g.
"share"/"open with") delegate to other apps you choose.

## 4. Data deletion

Deleting files or a vault inside the app removes them from your device. Uninstall
the app to remove app settings and cached copies of opened files. Files you
created or moved outside app-private storage remain on your device after
uninstall, as you would expect from a file manager.

## 5. Children's privacy

The app does not collect personal information from anyone, including children.

## 6. Changes & contact

If this policy changes, the new version will be published here. Questions:
open an issue on the app's public repository (see the app's Play Store listing
for the repository link).
