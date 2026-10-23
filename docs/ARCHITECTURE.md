
# Architecture

PDF Helper is intentionally small. This document explains the shape of the
codebase so a contributor can find where things live without reading every
file. It is not a replacement for the code — it is a map.

---

## High-level

```
┌───────────────────────────────────────────┐
│  MainActivity (single Activity)           │
│    ↳ setContent { PdfHelperTheme { … } }  │
└───────────────────┬───────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────┐
│  AppNavHost (Navigation Compose)          │
│   LOADING → DASHBOARD → TOOL / ABOUT      │
└───────────────────┬───────────────────────┘
                    │
      ┌─────────────┼─────────────┐
      ▼             ▼             ▼
┌──────────┐ ┌──────────┐ ┌──────────────┐
│ Dashboard│ │  12 tools│ │   About      │
│ Screen   │ │  Screens │ │   Screen     │
└──────────┘ └────┬─────┘ └──────────────┘
                  │
                  ▼
      ┌─────────────────────┐
      │  *ViewModel         │ (per-tool)
      │  StateFlow<UiState> │
      │  viewModelScope     │
      └──────────┬──────────┘
                 │  Dispatchers.IO
                 ▼
      ┌─────────────────────┐
      │  PdfEngine          │  ← PDFBox wrapper
      │  FileIo             │  ← SAF + FileProvider
      └─────────────────────┘
```

---

## Layers

### 1. UI layer (`ui/`)

**Compose only.** No XML layouts beyond what's required for the theme and
manifest.

```
ui/
├── AppNavHost.kt          — NavHost + route wiring + Phase 2 stub
├── catalog/
│   └── ToolCatalog.kt     — single source of truth for dashboard tools
├── components/
│   └── ToolScaffold.kt    — shared building blocks (scaffold, buttons,
│                            result banner, processing overlay)
├── model/
│   └── Tool.kt            — data class driving the dashboard
├── nav/
│   └── Destinations.kt    — every route string, one file
├── screen/
│   ├── LoadingScreen.kt
│   ├── DashboardScreen.kt
│   ├── AboutScreen.kt
│   └── tools/
│       ├── MergeScreen.kt
│       ├── SplitScreen.kt
│       ├── RotateScreen.kt
│       ├── ReorderScreen.kt
│       ├── RemovePagesScreen.kt
│       ├── ImagesToPdfScreen.kt
│       ├── TextToPdfScreen.kt
│       ├── PdfToImagesScreen.kt
│       ├── AddContentScreen.kt
│       ├── WatermarkScreen.kt
│       ├── PageNumbersScreen.kt
│       └── PasswordScreen.kt
└── theme/
    ├── Color.kt
    ├── Type.kt
    └── Theme.kt
```

Every screen is stateless: it takes callbacks (`onBack`, `onToolClick`) and
reads state from its ViewModel. Screens that render PDF output never touch
`PDDocument` directly.

### 2. ViewModel layer (`ui/screen/tools/*ViewModel`)

Each tool file contains its own `AndroidViewModel` and its own `UiState` data
class. No shared ViewModels, no cross-tool state.

**Pattern for every tool:**

```kotlin
data class FooUiState(
    val sourceUri: Uri? = null,
    val isWorking: Boolean = false,
    val resultUri: Uri? = null,
    val resultName: String = "",
    val error: String? = null,
    // tool-specific fields…
)

class FooViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(FooUiState())
    val state: StateFlow<FooUiState> = _state.asStateFlow()

    fun loadFile(context: Context, uri: Uri) { /* Dispatchers.IO */ }
    fun apply() { /* Dispatchers.IO, stage result via PdfEngine */ }
}
```

**Why `AndroidViewModel`:** every tool needs a `Context` to talk to the
`contentResolver`. `AndroidViewModel` gives that without leaking the Activity
(it's the Application context). A factory-less default constructor also means
`viewModel()` works in the composable without ceremony.

**Why one file per tool:** the ViewModel, the `UiState` data class, and the
`@Composable` are cohesive. Splitting them across three files adds navigation
cost with no benefit at this scale.

### 3. Engine layer (`pdf/`, `io/`)

Two abstractions stand between the ViewModels and the file system.

#### `pdf/PdfEngine.kt`

Wraps every PDFBox call. ViewModels never touch `PDDocument` directly.

Responsibilities:

- **Load** — `load(context, uri)` reads SAF bytes and calls `load(bytes)`;
  translates `InvalidPasswordException` and `IOException` into the sealed
  `PdfError` hierarchy.
- **Save** — `save(doc)` returns a `ByteArray`. Never writes to a `Uri` itself.
- **Encryption** — `isEncrypted(bytes)`, `protect(doc, password)`,
  `removeProtection(bytes, password)`.
- **Staging** — `stageShare(context, name, bytes)` writes to `cache/shared/`
  and returns a `content://` URI through `FileIo`.

`PdfError` is a sealed class with four subclasses: `NeedsPassword`,
`WrongPassword`, `Corrupt`, `Io`. Every ViewModel maps these to a user message
via a `when` expression.

#### `io/FileIo.kt`

Handles everything that isn't PDF parsing:

- Reads bytes from a SAF URI (`contentResolver.openInputStream`)
- Writes bytes to a SAF URI (`contentResolver.openOutputStream`) — unused today
  but available for a future "save to folder" feature
- Queries display name via `OpenableColumns`
- Stages bytes into `cache/shared/` and returns a FileProvider URI
- Persists bytes into `files/outputs/` (app-private)
- Provides MIME presets (`PDF_MIMES`, `IMAGE_MIMES`, `TEXT_MIMES`)

`FileIo` is the only place that knows about `contentResolver` and
`FileProvider`. Everything else goes through it.

---

## Data flow — a single tool call

Example: **Merge PDFs**.

```
1. User taps Merge on Dashboard
   → navController.navigate(Destinations.MERGE)

2. MergeScreen composable mounts
   → MergeViewModel created via viewModel()
   → reads MergeUiState(empty list of items)

3. User taps "Choose files"
   → rememberLauncherForActivityResult(OpenMultipleDocuments)
   → returns List<Uri>
   → vm.addFiles(context, uris)
      • reads display name via FileIo
      • updates _state.items

4. User taps "Apply"
   → vm.merge()
      • _state.isWorking = true  (screen shows ProcessingOverlay)
      • viewModelScope.launch(Dispatchers.IO) { mergeOnDisk(…) }
        • for each Uri: FileIo.readBytes → PdfEngine.load
        • PDFMergerUtility.appendDocument into a fresh PDDocument
        • PdfEngine.save → ByteArray
        • PdfEngine.stageShare → content:// URI
      • _state.isWorking = false
      • _state.resultUri = <content:// URI>
      • _state.resultName = "merged-<ts>.pdf"

5. Screen shows ResultBanner + Share button
   → Share: ACTION_SEND with the content:// URI
   → System chooser appears, user picks target app
```

No network, no filesystem exposure beyond the specific URIs the user picked,
no persistence beyond `cache/shared/` (which Android may clear at any time).

---

## Navigation

`AppNavHost` uses the standard `NavHost` + `composable` pattern.

- **Start destination:** `LOADING`.
- **`LOADING`** auto-transitions to `DASHBOARD` after 1.1 s (via
  `popUpTo(LOADING) { inclusive = true }` — back from Dashboard exits the app,
  not returns to the spinner).
- **`DASHBOARD`** routes to tools by `onToolClick(route)` and to `ABOUT` by
  `onAboutClick()`.
- **Tool routes** are wired through a `toolRoute(nav, route) { back -> XScreen(onBack = back) }` helper — each tool composable receives `onBack = popBackStack` automatically.
- **`EDIT_PDF`** is still a `toolStub` (Phase 2 placeholder).

All tool routes are string constants in `Destinations`. Adding a tool requires:

1. New constant in `Destinations`
2. New `Tool(...)` entry in `ToolCatalog`
3. New `toolRoute(navController, Destinations.X) { back -> XScreen(onBack = back) }` line in `AppNavHost`
4. The screen file itself

Four files touched. No route arguments today — tools manage their own state
via `rememberLauncherForActivityResult`, not via `NavType` arguments.

---

## Theming

Single `PdfHelperTheme` wrapper (in `ui/theme/Theme.kt`) applied at
`MainActivity.setContent`. Every screen lives inside it.

Two full `ColorScheme`s (`LightColors`, `DarkColors`) — every slot assigned, no
Material default purple leaking through. `PdfHelperTheme` also runs a
`SideEffect` that syncs the status-bar icon color with the active theme on
every recompose.

Dark/light is driven by `isSystemInDarkTheme()` — no in-app toggle yet. When
added, `PdfHelperTheme` will gain an override parameter and the Settings screen
will pass `AppCompatDelegate.setDefaultNightMode(...)`.

Material You dynamic color is **off** by design — a document utility should
look the same across devices.

---

## What is deliberately absent

- **No dependency injection framework.** Hilt/Koin would add 200+ lines of
  setup for a single Activity. Every dependency is either created at the
  composable (via `viewModel()`) or is a top-level `object`.
- **No repository layer.** A repository would wrap `PdfEngine` and `FileIo`
  — which already act as repositories. Adding a layer between them and the
  ViewModels would be pure ceremony.
- **No WorkManager / background jobs.** All work happens while a screen is
  on-screen. If a tool's operation takes more than a few seconds, that's a
  bug to fix, not a use case to design around.
- **No local database.** No state is persisted between app sessions.
- **No analytics, crash reporting, or logging framework.** The app cannot
  reach the internet; adding telemetry would require removing the privacy
  guarantee.
- **No Gradle wrapper.** CI installs Gradle via `gradle/actions/setup-gradle`.
  Local contributors use their installed Gradle (8.11+) or Android Studio's
  bundled one.

---

## How to add a new tool

1. **Design the state.** What does the user pick, what do they configure, what
   is the output? Write the `UiState` data class first.
2. **Write the ViewModel.** Follow the existing pattern: `MutableStateFlow`
   → `Dispatchers.IO` → `PdfEngine` / `FileIo` → update state.
3. **Write the composable.** Use `ToolScaffold` + `PrimaryActionButton` +
   `ResultBanner`. Don't rebuild those primitives.
4. **Add the route.** `Destinations.kt` constant.
5. **Add the catalog entry.** `ToolCatalog.kt` with title/desc/icon strings.
6. **Wire it.** One line in `AppNavHost.kt` via `toolRoute`.
7. **Add strings.** `values/strings.xml` — no hardcoded text.
8. **Test the release build** (`gradle assembleRelease`), not just debug — R8
   will strip anything reflective you forgot to keep.

That's it. No boilerplate, no annotations, no framework to learn.

---

## Testing

Today there are no automated tests. This is a known gap — see
[ROADMAP.md](ROADMAP.md) under "How to help." Contributions adding:

- **Pure unit tests** for `PdfEngine.parseRanges`-style helpers, `sanitize`,
  `wrapText`, and any other pure function
- **Robolectric tests** for `FileIo` behavior against a fake `contentResolver`
- **Instrumented tests** for at least the Merge tool as a smoke test

would be welcomed and prioritized.

Until then, manual test procedure before a release:

1. Every tool opens, processes a small PDF, and the result opens in another
   viewer.
2. Both light and dark modes render correctly.
3. Release build (`gradle assembleRelease`) installs and every tool runs
   without a `ClassNotFoundException` (proves R8 rules still cover everything
   that needs them).
