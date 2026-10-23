# Privacy Policy

**Last updated:** 2025

PDF Helper has no privacy policy because there is nothing to disclose. This
document exists to explain, in plain language, exactly what the app does and
does not do with your files, and why those guarantees are technically enforced
rather than merely promised.

---

## Short version

- **Your files never leave your device.** All processing happens locally.
- **No account, no sign-in, no email.** There is no user database to sign up for.
- **No analytics, no telemetry, no crash reporting.** The app has no internet
  permission, so it cannot send anything anywhere even if a library tried.
- **No third-party SDKs.** The dependencies are PDFBox-Android, AndroidX,
  Kotlin Coroutines, and BouncyCastle — all auditable, all open source.

---

## What the app does with your files

When you pick a PDF or image:

1. Android's Storage Access Framework hands the app a temporary read-only
   permission to **that specific file**.
2. The app reads the file's bytes into memory (or streams them, for large
   files).
3. The requested operation runs entirely on the CPU of your device.
4. The result is written either to the app's private cache (for sharing to
   another app) or to a location you choose via SAF.
5. The read permission expires when the app closes.

At no point does any part of this process touch a network socket. There is no
network socket to touch.

---

## Why the guarantee is real, not marketing

The Android OS enforces permissions at the kernel level. An app without
`android.permission.INTERNET` in its manifest cannot open a network connection —
any attempt throws `SecurityException` and the process is killed. That is not a
policy the app can choose to violate later; it is a hard boundary.

The manifest in this repository does not contain the `INTERNET` permission. You
can verify this yourself in two ways:

### Option 1 — Read the source

Open `app/src/main/AndroidManifest.xml` in the repository. Search for
`INTERNET`. You will find it only in a comment explaining its deliberate
absence.

### Option 2 — Inspect the built APK

Install [aapt2](https://developer.android.com/studio/command-line/aapt2) or use
any APK analyzer app (for example, Apktool, `apktool d app.apk`, then read
`AndroidManifest.xml`). Look through `<uses-permission>` entries. You will find
none related to networking.

---

## What permissions the app requests

**None.** The manifest declares zero `<uses-permission>` entries.

Specifically:

| Permission | Status |
| :--- | :--- |
| `INTERNET` | **Not requested** |
| `ACCESS_NETWORK_STATE` | **Not requested** |
| `READ_EXTERNAL_STORAGE` | **Not requested** (SAF used instead) |
| `WRITE_EXTERNAL_STORAGE` | **Not requested** (SAF used instead) |
| `READ_MEDIA_IMAGES` | **Not requested** (SAF used instead) |
| `CAMERA` | **Not requested** |
| `LOCATION` (any variant) | **Not requested** |
| `CONTACTS` | **Not requested** |
| `SMS` / `CALL_LOG` | **Not requested** |

File picking, saving, and sharing all go through Android's Storage Access
Framework, which grants per-file, per-session access without any manifest
permission.

---

## What data the app stores

### On your device

- **`cache/shared/`** — Temporary directory used to stage generated PDFs and
  images for the system share-sheet. Files are removed by Android automatically
  when storage is tight, and are not backed up.
- **`files/outputs/`** — Optional persistent directory (currently unused;
  reserved for a future "save in app" feature). Files here survive reboots but
  are removed on uninstall.

Both directories are inside the app's private sandbox. Other apps cannot read
them without going through the FileProvider — and even then, only files you
explicitly share get a `content://` URI.

### Backups

`android:allowBackup="false"` in the manifest disables Android's cloud backup
for this app entirely. Nothing in the app's private storage is sent to Google
Drive or any other service.

### Crash reports

None are generated. When the app crashes, Android shows the standard system
crash dialog and writes a stack trace to your local device log. Nothing is sent
anywhere. If you choose to send us the trace yourself (via a GitHub issue or
Telegram message), that is your decision.

---

## What this website does

`pdf-app.ximanta.xyz` is a static HTML page hosted on GitHub Pages.

- **No cookies.** Nothing is stored in your browser by this site.
- **No analytics.** There is no Google Analytics, Plausible, Fathom, or any
  other tracker.
- **No external JavaScript beyond what is visible in `index.html`** — one
  script (`js/main.js`) fetches `data/latest.json` to display the current
  release. It uses `cache: 'no-store'` and no credentials.
- **Google Fonts.** The page loads Inter and Instrument Serif from Google's
  font CDN. Google's own privacy policy applies to that request. If you prefer
  to avoid it, use a browser extension like "Local Font Access" or a font
  blocker; the page falls back to system fonts gracefully.
- **GitHub Pages.** The site is served by GitHub, whose server logs (standard
  HTTP access logs) apply.

There is no comment system, no embed, and no third-party widget.

---

## What changes to this policy look like

If a future version of the app ever needs to add a permission, request a
network connection, or transmit any data, this file will be updated with:

1. A clear explanation of the change
2. A description of what data will be sent and to whom
3. A prominent note in the release notes for that version
4. (For significant changes) A migration path that allows users to keep
   using an older version

Given the entire premise of the app, this is extremely unlikely. But the
documented commitment matters.

---

## Contact

Questions, concerns, or a bug report about privacy:

- **GitHub issue:** [github.com/realximanta/PDF-helper/issues](https://github.com/realximanta/PDF-helper/issues)
- **Telegram:** [@b4nzw](https://t.me/b4nzw)

If you find a genuine privacy violation — a permission the app should not have,
a network call it makes, or data it exposes — please open a security issue (or
message directly via Telegram) rather than a public bug report, so that
affected users can be notified before a fix lands.

---

## Summary table

| Question | Answer |
| :--- | :--- |
| Does the app upload my files? | **No** (no INTERNET permission) |
| Does the app collect analytics? | **No** |
| Does the app use cookies (on device or web)? | **No** |
| Does the app require an account? | **No** |
| Does the app send crash reports? | **No** |
| Does the app need storage permissions? | **No** (SAF only) |
| Does the app need location permissions? | **No** |
| Does the app run in airplane mode? | **Yes, fully** |
| Is the source code public? | **Yes, MIT-licensed** |
| Can I verify the privacy claims? | **Yes, read or rebuild the source** |
