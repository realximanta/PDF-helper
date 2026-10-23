# Third-Party Licenses

PDF Helper is MIT-licensed (see [LICENSE](LICENSE)). It depends on the
following open-source libraries, each of which retains its own license. The
full text of each dependency's license is available in the linked source
repository or via the artifact on Maven Central.

**Every dependency listed here is compatible with MIT** — there is no
copyleft (GPL/AGPL) in the runtime classpath.

---

## Runtime dependencies

### com.tom-roush:pdfbox-android

- **Version:** 2.0.27.0
- **License:** Apache License 2.0
- **Source:** https://github.com/TomRoush/PdfBox-Android
- **Used for:** All PDF parsing, manipulation, encryption, and content-stream
  writes. Port of Apache PDFBox 2.0.x for Android.

### org.apache.commons:commons-logging (transitive)

- **License:** Apache License 2.0
- **Source:** https://commons.apache.org/proper/commons-logging/
- **Used for:** Logging bridge used internally by PDFBox.

### org.bouncycastle:bcprov-jdk15to18 and bcpkix-jdk15to18 (transitive)

- **License:** MIT-style (Bouncy Castle License)
- **Source:** https://www.bouncycastle.org/java.html
- **Used for:** Cryptographic primitives for PDF password encryption and
  decryption (delegated to by PDFBox).

### com.tom-roush:pdfbox-android → bundled Apache Harmony (transitive)

- **License:** Apache License 2.0
- **Source:** https://harmony.apache.org/
- **Used for:** AWT-compatible shims PDFBox depends on (font metrics, image
  handling) that don't exist on Android.

---

### androidx.* (Google / Android Jetpack)

- **Version:** See `app/build.gradle.kts`
- **License:** Apache License 2.0
- **Source:** https://github.com/androidx
- **Sub-libraries used:**
  - `androidx.core:core-ktx`
  - `androidx.activity:activity-compose`
  - `androidx.lifecycle:lifecycle-runtime-ktx`
  - `androidx.lifecycle:lifecycle-viewmodel-compose`
  - `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview`, `ui-tooling`
  - `androidx.compose.material3:material3`
  - `androidx.compose.material:material-icons-extended`
  - `androidx.navigation:navigation-compose`
  - `androidx.documentfile:documentfile`

### org.jetbrains.kotlinx:kotlinx-coroutines-android

- **Version:** 1.9.0
- **License:** Apache License 2.0
- **Source:** https://github.com/Kotlin/kotlinx.coroutines
- **Used for:** Async I/O and ViewModel scopes.

### Kotlin Standard Library

- **Version:** Delivered with Kotlin 2.0.21 via AGP
- **License:** Apache License 2.0
- **Source:** https://github.com/JetBrains/kotlin
- **Used for:** The language itself.

---

## Fonts (not bundled — served by the OS)

The app uses **Roboto** via `FontFamily.SansSerif` in Compose, which is the
system default font on Android and is **not bundled** in the APK. Roboto is
licensed under the Apache License 2.0 (https://github.com/googlefonts/roboto).

The landing page at `pdf-app.ximanta.xyz` loads **Inter** and
**Instrument Serif** via Google Fonts. Both are SIL Open Font License 1.1:

- Inter: https://github.com/rsms/inter/blob/master/LICENSE.txt
- Instrument Serif: https://fonts.google.com/specimen/Instrument+Serif

The page uses **JetBrains Mono** for code blocks, licensed under the SIL Open
Font License 1.1 (https://github.com/JetBrains/JetBrainsMono).

---

## Icons

The Android launcher icon is a hand-authored `VectorDrawable` created for this
project and covered by the project's MIT license. No third-party icon sets are
bundled in the app.

The landing page uses inline SVGs written for this project (also MIT).
Lucide icons were not used — the minimal redesign uses a single 4-path document
glyph.

---

## Development-only dependencies

These are not shipped in the APK but are used during development:

- **Kotlin compiler** — Apache License 2.0
- **Android Gradle Plugin** — Apache License 2.0
- **Gradle** — Apache License 2.0
- **Jetpack Compose Compiler** — Apache License 2.0

---

## GitHub Actions used by CI

The release workflow references these actions, each under its own license:

| Action | License |
| :--- | :--- |
| `actions/checkout@v4` | MIT |
| `actions/setup-java@v4` | MIT |
| `android-actions/setup-android@v3` | MIT |
| `gradle/actions/setup-gradle@v4` | Apache License 2.0 |
| `softprops/action-gh-release@v2` | MIT |

Actions are executed inside GitHub's runner environments and are not bundled
into the produced APK.

---

## Obtaining full license texts

If you need the verbatim text of any license above (for example, to comply
with Apache-2.0's requirement to include the license text in redistributions),
the authoritative source is the artifact on Maven Central. The license texts
are also included in the `.pom` and `LICENSE` files inside each AAR/JAR, which
are extracted into `~/.gradle/caches/modules-2/files-2.1/` after a build.

For your convenience, the canonical license texts:

- **MIT:** https://opensource.org/license/mit/
- **Apache 2.0:** https://www.apache.org/licenses/LICENSE-2.0
- **Bouncy Castle License:** https://www.bouncycastle.org/license.html
- **SIL OFL 1.1:** https://openfontlicense.org/

---

## Notices required by Apache-2.0

Per Section 4(d) of the Apache License 2.0, if you redistribute this app or a
binary derived from it, you must include copies of the Apache License 2.0 for
the bundled dependencies (PDFBox, AndroidX, Kotlin, Bouncy Castle) in your
distribution. The project bundles these in standard form; if you fork the app
and change the dependency set, re-verify compliance.
