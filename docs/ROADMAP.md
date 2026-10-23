# Roadmap

This document tracks where PDF Helper is now and where it's going. Dates are
intentionally absent — a one-person project shouldn't promise release windows.

## Phase 1 — v1.0.0 (current)

The MVP. Every tool listed here is implemented, tested locally, and shipped in
the initial signed release.

### Organize
- [x] **Merge** — combine multiple PDFs in order
- [x] **Split** — extract a range or every page
- [x] **Rotate** — per-page 90° / 180° / 270°
- [x] **Reorder** — move pages up/down
- [x] **Remove pages** — tick to delete

### Convert
- [x] **Images → PDF** — JPG/PNG with automatic compression
- [x] **Text / Markdown → PDF** — paginated, A4 or Letter
- [x] **PDF → Images** — PNG or JPEG at 1x/2x/3x

### Edit
- [x] **Add text & images** — overlay without touching the original
- [x] **Watermark** — text or image, opacity control
- [x] **Page numbers** — any corner, any format

### Secure
- [x] **Password protect** — 128-bit standard encryption
- [x] **Password unlock** — remove a password you know

### Infrastructure
- [x] GitHub Actions pipeline (tag-triggered, signed APK)
- [x] Custom domain website with auto-updating release metadata
- [x] PWA-ready static site (no build step, no cookies, no analytics)
- [x] MIT license, contribution guide, code of conduct

---

## Phase 2 — Advanced editor (v2.0)

The genuinely hard part. Each item below is planned as its own minor release
so that a regression in one doesn't block the others.

### v2.0 — Text editing foundations
- [ ] **Detect existing text** in a PDF page and its bounding box
- [ ] **Match original font** as closely as possible (may require font
      substitution when the PDF embeds a subsetted or Type 3 font)
- [ ] **Replace text** in place, preserving surrounding layout
- [ ] **Insert text** at arbitrary coordinates with a font picker
      (bundled: Inter, Source Serif, JetBrains Mono)

### v2.1 — Image editing
- [ ] **Replace existing images** in place
- [ ] **Move images** to new coordinates on the same page
- [ ] **Remove images** without disturbing surrounding content

### v2.2 — Multi-font / multi-style documents
- [ ] **Mixed-font text blocks** — different font per paragraph or run
- [ ] **Rich text overlay** — bold / italic / underline within a single block
- [ ] **Font import** — load a `.ttf` file the user picks and embed it

### v2.3 — Signing workflow
- [ ] **Fill signature fields** (draw / upload image)
- [ ] **Strip existing signature** cleanly (currently a side effect of save)
- [ ] **Explain the invalidation** before applying

### v2.4 — OCR (stretch)
- [ ] **Scanned PDF → searchable PDF** via Tesseract.js WASM equivalent
- [ ] Optional download of language packs
- [ ] Heavily marked as optional because it doubles the APK size

---

## Won't do

Explicitly out of scope. Requests for these will be closed as "won't fix."

- **Server-side processing.** The privacy model is the entire point. If a
  feature requires a network call, it doesn't belong in this app.
- **PDF → Word / Excel / PowerPoint.** LibreOffice-grade conversion needs a
  full office suite; there is no way to ship that in a phone APK.
- **Cloud sync / accounts.** No login, no account, no data leaves the device.
- **Ads.** No network permission means no ads can load, and no ads would be
  added even if they could.
- **Telemetry of any kind.** No analytics, no crash reporters, no usage
  counters.
- **Play Store listing.** Ship signed APKs from GitHub. Keeps the pipeline
  open and auditable.

---

## How to help

The best way to accelerate Phase 2 is to:

1. **Open an issue** describing the specific PDF scenario that breaks (attach
   a repro if possible).
2. **Write tests** for existing Phase 1 tools — none exist yet, and adding
   them frees maintainer time for Phase 2 work.
3. **Report edge cases** — encrypted PDFs, malformed files, huge documents,
   fonts with unusual glyph sets.

See `CONTRIBUTING.md` for the workflow.
