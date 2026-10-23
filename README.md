
# PDF Helper

**Free, private, offline PDF tools for Android.**

Every operation runs entirely on your device. No uploads, no accounts, no tracking — because the app doesn't have the `INTERNET` permission, it literally cannot phone home.

[![Latest Release](https://img.shields.io/github/v/release/realximanta/PDF-helper?style=flat-square&color=2563EB&label=release)](https://github.com/realximanta/PDF-helper/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-2563EB?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-16A34A?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Material 3](https://img.shields.io/badge/Material%203-Compose-4285F4?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Website](https://img.shields.io/badge/website-pdf--app.ximanta.xyz-2563EB?style=flat-square&logo=googlechrome&logoColor=white)](https://pdf-app.ximanta.xyz)

[![Build](https://img.shields.io/github/actions/workflow/status/realximanta/PDF-helper/release.yml?style=flat-square&label=build)](https://github.com/realximanta/PDF-helper/actions)
[![Last Commit](https://img.shields.io/github/last-commit/realximanta/PDF-helper?style=flat-square)](https://github.com/realximanta/PDF-helper/commits/main)
[![Issues](https://img.shields.io/github/issues/realximanta/PDF-helper?style=flat-square)](https://github.com/realximanta/PDF-helper/issues)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-16A34A?style=flat-square)](CONTRIBUTING.md)
[![APK Size](https://img.shields.io/badge/APK-~8%20MB-lightgrey?style=flat-square)](https://github.com/realximanta/PDF-helper/releases/latest)
[![Stars](https://img.shields.io/github/stars/realximanta/PDF-helper?style=flat-square&color=yellow)](https://github.com/realximanta/PDF-helper/stargazers)

---

## Download

Grab the latest signed APK from the **[Releases page](https://github.com/realximanta/PDF-helper/releases/latest)**, or visit **[pdf-app.ximanta.xyz](https://pdf-app.ximanta.xyz)**.

Requires **Android 8.0 (API 26)** or newer. Not on the Play Store — signed builds ship straight from GitHub Actions.

---

## Tools

| Organize | Convert | Edit | Secure |
| :--- | :--- | :--- | :--- |
| Merge PDFs | Images → PDF (auto-compressed) | Add text & images | Password protect |
| Split PDF | Text / Markdown → PDF | Watermark | Password unlock |
| Rotate pages | PDF → Images (PNG/JPEG) | Page numbers | |
| Reorder pages | | Advanced editor *(Phase 2)* | |
| Remove pages | | | |

---

## Why this exists

Most free PDF sites upload your file, process it on a server, and hand it back. Even well-meaning operators end up with copies on their disks. PDF Helper removes that risk entirely — the app runs offline, has zero `<uses-permission>` entries in its manifest, and uses Android's Storage Access Framework for file access. The privacy guarantee is enforced by the OS kernel, not a policy page.

See **[docs/PRIVACY.md](docs/PRIVACY.md)** for the full explanation and verification steps.

---

## Documentation

| Document | Purpose |
| :--- | :--- |
| **[docs/PRIVACY.md](docs/PRIVACY.md)** | Full privacy policy + how to verify the no-network claim |
| **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** | How the code is organized, data flow, how to add a tool |
| **[docs/ROADMAP.md](docs/ROADMAP.md)** | Phase 1 (shipped) and Phase 2 (planned) features |
| **[CONTRIBUTING.md](CONTRIBUTING.md)** | Dev setup, coding standards, PR rules |
| **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** | Community standards (Contributor Covenant 2.1) |
| **[SECURITY.md](SECURITY.md)** | How to report vulnerabilities privately |
| **[CHANGELOG.md](CHANGELOG.md)** | Release history (Keep a Changelog format) |
| **[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)** | Attribution for every dependency |
| **[LICENSE](LICENSE)** | MIT |

---

## Tech Stack

- **Kotlin 2.0** + **Jetpack Compose** (Material 3)
- **PDFBox-Android** for manipulation, **PdfRenderer** for rasterization
- **Min SDK 26** · **Target SDK 35** · **JVM 17**
- **Single Activity** architecture — no fragments, no XML layouts
- **No backend.** Ever.

---

## Build

Releases are built and signed in GitHub Actions from `v*` tags. Local build:

```bash
gradle assembleRelease
```

No Gradle wrapper is committed — CI installs Gradle via `gradle/actions/setup-gradle`. Without `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` env vars, the release APK is unsigned (fine for testing).

See **[CONTRIBUTING.md](CONTRIBUTING.md)** for full dev setup.

---

## Release Flow

1. Bump `versionName` in `app/build.gradle.kts` (e.g. `1.0.0` → `1.1.0`).
2. Commit and push to `main`. **No build runs.**
3. Create tag `v1.1.0` and push it.
4. CI verifies `tag == versionName`, builds the signed APK, publishes a GitHub Release, and updates `data/latest.json` on `main`.
5. The website picks up the new version on next visit.

Tag pushes are the **only** trigger. Pushing to `main` never builds.

---

## Contributing

Issues and pull requests welcome. Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** first — it explains the coding standards, the testing checklist, and why large PRs without a prior issue get declined.

---

## Author

**Himanta Bhuyan** (realximanta)

[![GitHub](https://img.shields.io/badge/GitHub-realximanta-181717?style=flat-square&logo=github)](https://github.com/realximanta)
[![X](https://img.shields.io/badge/X-realximanta-000000?style=flat-square&logo=x)](https://x.com/realximanta)
[![Telegram](https://img.shields.io/badge/Telegram-b4nzw-26A5E4?style=flat-square&logo=telegram&logoColor=white)](https://t.me/b4nzw)
[![Website](https://img.shields.io/badge/ximanta.xyz-2563EB?style=flat-square&logo=googlechrome&logoColor=white)](https://ximanta.xyz)

---

## License

MIT — see **[LICENSE](LICENSE)**. © 2025 Himanta Bhuyan.

Dependency licenses documented in **[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)**.
