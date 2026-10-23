
# PDF Helper

**Free, private, offline PDF tools for Android.**

Every operation runs entirely on your device. No uploads, no accounts, no tracking, no internet permission — because the app literally cannot make a network request.

[![Release APK](https://img.shields.io/github/v/release/realximanta/PDF-helper?label=download&style=flat-square)](https://github.com/realximanta/PDF-helper/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)
[![Android](https://img.shields.io/badge/android-8.0%2B-green?style=flat-square)](https://developer.android.com)

---

## Download

Grab the latest signed APK from the [**Releases page**](https://github.com/realximanta/PDF-helper/releases/latest), or visit **[pdf-app.ximanta.xyz](https://pdf-app.ximanta.xyz)**.

The app requires **Android 8.0 (API 26)** or newer. There is no Play Store listing — releases ship directly from GitHub.

## Privacy

- ❌ No `INTERNET` permission in the manifest
- ❌ No storage permissions — uses Android's scoped Storage Access Framework
- ❌ No analytics, no telemetry, no crash reporting
- ✅ Every byte of every PDF stays on your device

The privacy claim is enforced at the OS level, not just as a policy. Android will kill the process if any library ever tries to open a socket.

## Tools

| Organize | Convert | Edit | Secure |
| :--- | :--- | :--- | :--- |
| Merge PDFs | Images → PDF (auto-compressed) | Add text & images | Password protect |
| Split PDF | Text / Markdown → PDF | Watermark | Password unlock |
| Rotate pages | PDF → Images (PNG/JPEG) | Page numbers | |
| Reorder pages | | Advanced editor *(Phase 2)* | |
| Remove pages | | | |

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **PDFBox-Android** for manipulation, **PdfRenderer** for rasterization
- **Min SDK 26** · **Target SDK 35** · **JVM 17**
- **No backend.** Ever.

## Build

Releases are built and signed in GitHub Actions from `v*` tags. Building locally requires Android Studio or a Gradle install (no wrapper is committed — CI uses `gradle/actions/setup-gradle`).

```bash
gradle assembleRelease
```

Without `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` env vars, the release APK will be unsigned — which is fine for testing.

## Release Flow

1. Bump `versionName` in `app/build.gradle.kts` (e.g. `1.0.0` → `1.1.0`).
2. Commit and push to `main`. **No build runs.**
3. Create a tag `v1.1.0` and push it.
4. GitHub Actions builds, signs, and publishes the APK + updates `web/data/latest.json`.
5. The website picks up the new version on next visit.

## Contributing

Issues and pull requests are welcome. Before opening a large PR, please open an issue to discuss the scope.

## License

[MIT](LICENSE) © 2025 Himanta Bhuyan

## Links

- **Author:** [Himanta Bhuyan](https://github.com/realximanta) (aka realximanta)
- **GitHub:** [@realximanta](https://github.com/realximanta)
- **X:** [@realximanta](https://x.com/realximanta)
- **Telegram:** [@b4nzw](https://t.me/b4nzw)
- **Website:** [pdf-app.ximanta.xyz](https://pdf-app.ximanta.xyz)
