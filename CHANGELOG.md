# Changelog

All notable changes to PDF Helper are documented here. This project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Release tags are `v<major>.<minor>.<patch>` and always match `versionName` in
`app/build.gradle.kts` — the CI workflow enforces this and fails the build if
they diverge.

---

## [Unreleased]

### Planned for v2.x
- Advanced PDF editor: edit existing text in its original font
- Replace and move existing images in place
- Mixed-font documents — different font per paragraph or run
- Font import — load and embed a `.ttf` of your choice
- Signature field filling, with clear warning about invalidation

See [docs/ROADMAP.md](docs/ROADMAP.md) for the full plan.

---

## [1.0.0] — 2025

First public release. Every tool below runs entirely on-device; the app has no
`INTERNET` permission and cannot upload anything.

### Added — Organize

- **Merge PDFs** — combine multiple PDFs in any order via
  `PDFMergerUtility.appendDocument`, one document per row with a remove action.
- **Split PDF** — extract a custom range (`1-3, 5, 7-9`) or every page into a
  new document via `PDDocument.importPage`.
- **Rotate pages** — per-page 90° / 180° / 270° rotation, plus quick
  "rotate all left/right" actions. Rotations accumulate and are applied
  relative to each page's current orientation.
- **Reorder pages** — move pages up/down with live re-numbering; a fresh
  document is built from the new page order.
- **Remove pages** — tick pages to delete (with `All` / `None` shortcuts); the
  resulting document excludes them entirely.

### Added — Convert

- **Images → PDF** — JPG/PNG to PDF with automatic compression:
  `BitmapFactory` inJustDecodeBounds + power-of-2 `inSampleSize` downsampling,
  scale to ≤1920 px on the longest edge, JPEG re-encode at a
  user-controlled quality (40–95%, default 75%), page size derived from image
  aspect ratio. Designed to keep multi-image PDFs well under 100 MB.
- **Text / Markdown → PDF** — load any `.txt`/`.md` file or paste text, choose
  A4 or Letter, and generate a paginated PDF with Helvetica. Text is
  width-wrapped using `getStringWidth` and sanitized to WinAnsi.
- **PDF → Images** — render any page range as PNG or JPEG at 1x/2x/3x
  resolution using the native `PdfRenderer` (no PDFBox rasterization —
  memory-friendly for large documents).

### Added — Edit

- **Add text & images** — overlay new text (font size 6–96) and/or an image
  at any corner or center of any page range, using
  `PDPageContentStream(APPEND)` so the original content is untouched.
- **Watermark** — text or image watermark, rotated ~45°, with adjustable
  opacity (5–90%) applied via `PDExtendedGraphicsState.nonStrokingAlphaConstant`.
- **Page numbers** — stamp page numbers in any corner with a customizable
  format (`{n} / {total}` by default), adjustable start number and font size.

### Added — Secure

- **Password protect** — apply 128-bit standard encryption with a user-provided
  password via `StandardProtectionPolicy`.
- **Password unlock** — remove a password you already know
  (`setAllSecurityToBeRemoved(true)`). Digitally signed PDFs become unsigned
  as a side-effect; the UI warns about this.

### Added — Interface & infrastructure

- Single-activity Jetpack Compose app, Material 3, edge-to-edge
- Light and dark themes with full `ColorScheme` coverage — no Material default
  purple leaking into unassigned slots
- Animated loading screen (rotating + pulsing indicator, 1.1 s minimum)
- Dashboard with sectioned tool grid (Organize / Convert / Edit / Secure /
  Coming Soon)
- About screen with owner information and social links
- Debug + dark-mode `@Preview` on every screen
- Release-ready R8/ProGuard rules covering PDFBox reflection, BouncyCastle
  crypto, commons-logging, and coroutines
- GitHub Actions release pipeline:
  - Triggered **only** by `push` of a `v*` tag
  - Verifies the tag matches `versionName` before building
  - Builds, signs, and publishes a GitHub Release with the APK
  - Updates `data/latest.json` on `main` so the website auto-reflects the new
    version without a redeploy
- Static marketing/documentation website at `pdf-app.ximanta.xyz` — vanilla
  HTML/CSS/JS, no build step, no cookies, no analytics
- MIT license, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (Contributor Covenant
  2.1), `SECURITY.md`, `docs/PRIVACY.md`, `docs/ROADMAP.md`

### Security

- Manifest declares **zero** `<uses-permission>` entries. No `INTERNET`, no
  storage, no location — the privacy guarantee is enforced by Android at the
  kernel level.
- File access uses the Storage Access Framework exclusively.
- `android:allowBackup="false"` keeps app-private data out of cloud backups.
- Dependabot configured for Gradle and GitHub Actions (weekly, grouped
  minor/patch PRs).

### Known limitations

- Advanced PDF editor (edit existing text, replace/move images) is Phase 2 —
  tracked as a disabled "Coming soon" card on the dashboard.
- Digitally signed PDFs lose their signature after any modification — inherent
  to re-saving; warned about in the UI.
- Password removal requires the correct password; no brute-force path exists.
- Very large PDFs may exceed device memory on low-RAM hardware.

---

[Unreleased]: https://github.com/realximanta/PDF-helper/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/realximanta/PDF-helper/releases/tag/v1.0.0
