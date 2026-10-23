
# Contributing to PDF Helper

Thanks for considering a contribution. This project is maintained by one person
in his spare time, so a few ground rules keep everything smooth.

## Code of Conduct

Be respectful. Disagreement is fine; being a jerk isn't. Anything that makes
the project less welcoming to a first-time contributor is out of scope.

## Reporting bugs

Open an issue with:

- **What you did** (which tool, which file type, how many pages)
- **What you expected**
- **What actually happened** (including the exact error message)
- **Device + Android version** (Settings → About phone)
- **App version** (shown on the About screen, e.g. `v1.0.0`)

If the PDF is not sensitive, attaching a stripped-down repro helps enormously.
**Do not upload any PDF you don't own or don't want public.** Reproduce with a
blank PDF if possible.

## Suggesting features

Open an issue titled `feature: <short description>` and include:

- The problem you're trying to solve
- What you'd like the app to do
- Any tools/workarounds you use today

Features that require the internet are rejected by design — see the privacy
model in `README.md`.

## Development setup

The project does not commit a Gradle wrapper. Use your locally installed Gradle
(8.11+ recommended) or Android Studio with the bundled Gradle.

### Requirements

- **JDK 17** (Temurin or equivalent)
- **Android SDK Platform 35** + build-tools 35.0.0
- **Gradle 8.11+** on `PATH`, or Android Studio Ladybug or newer

### Build locally

```bash
# Debug build (unsigned, installable)
gradle assembleDebug

# Release build (unsigned unless env vars are set)
gradle assembleRelease
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Signing a release locally

Set these environment variables before `assembleRelease`:

```bash
export KEYSTORE_PATH=/path/to/release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
```

Without them, `assembleRelease` produces `app-release-unsigned.apk` — that's
expected and fine for local testing.

## Coding standards

- **Kotlin only.** No Java in `src/`.
- **Compose only.** No XML layouts beyond what's needed for manifest/theme.
- **No new permissions.** The manifest is intentionally minimal.
- **No new dependencies without justification.** If a task can be done with
  what's already imported (PDFBox, Compose, Coroutines), do it that way.
- **PDF operations run on `Dispatchers.IO`.** Never block the main thread.
- **Every tool lives in its own file** under `ui/screen/tools/`. Follow the
  existing pattern (UiState + ViewModel + Composable Screen).
- **Use `PdfEngine` and `FileIo`.** Do not touch `PDDocument` or
  `contentResolver` directly inside a screen — those abstractions exist so
  resource cleanup and error mapping stay consistent.
- **No `println` / `System.out`.** Use `android.util.Log` if you must, and
  guard it with `BuildConfig.DEBUG`.
- **No TODO comments in shipped code.** Either finish it or open an issue.

### Naming

- Composables: `PascalCase`.
- Routes: `snake_case` string constants in `Destinations`.
- Resource IDs: `snake_case` with a typed prefix (`tool_`, `action_`, `state_`).
- ViewModels: `<Screen>ViewModel`.

## Testing before a PR

At minimum, verify:

1. `gradle assembleDebug` succeeds with no new warnings.
2. Every tool you touched opens, processes a small PDF, and produces a valid
   result that opens in another viewer.
3. The app still builds with `gradle assembleRelease` (R8 enabled). R8 strips
   reflection-dependent code silently — a debug-only test is not enough.
4. Dark mode and light mode both render correctly for any new UI.

## Commit messages

Short imperative subject line, then an optional body:

```
Add page numbering format presets

Closes #42
```

Prefixes that help history readability: `add:`, `fix:`, `refactor:`, `docs:`,
`chore:`. Not enforced, just preferred.

## Pull requests

- **One concern per PR.** Don't mix a feature with a refactor.
- Reference the issue it closes.
- Include a screenshot if the change is visual.
- CI must pass. If it fails due to your change, fix it in the PR — don't open
  a follow-up to clean up.
- Large PRs without a prior issue will likely be declined. Discuss first.

## Releasing

Only the maintainer pushes `v*` tags. See `README.md` → **Release Flow** for
the sequence. Contributors don't need to bump `versionName`.

## License

By submitting a pull request you agree your contribution is licensed under the
project's MIT license.
