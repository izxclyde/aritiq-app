# Aritiq

Offline notes + calculator for Android. Write a list, type `total`, and the sum shows up
live — paper-notebook style. No accounts, no network, everything stored locally.

## Stack
- **Kotlin Multiplatform** (Android-first, single `composeApp` module) — Compose Multiplatform 1.8.2, Kotlin 2.2.20, Gradle 8.14
- **SQLDelight 2.1.0** — local database (`AritiqDatabase`)
- **Koin 4.0.0** — dependency injection
- **Material 3** + custom paper theme (cream/warm-brown light, sepia/warm dark) — WCAG AA across all text/background pairs

## Color system & accessibility

Both light and dark themes were audited against WCAG 2.1 AA standards (4.5:1 normal text,
3:1 large text).

| Check | Light | Dark |
|-------|-------|------|
| Body text on background | 13.1:1 ✅ | 12.5:1 ✅ |
| Body text on surface | 12.5:1 ✅ | 10.2:1 ✅ |
| Primary accents on surface | 6.0:1 ✅ | 6.1:1 ✅ |
| Surface elevation layers | 10.1–12.5:1 ✅ | 4.8–8.5:1 ✅ |
| Secondary (orange) on white | 3.8:1 (large text ✅) | — |

**Dark mode surface layering fix (v2026-07-16):** Surface containers were lightened so
elevated cards are visibly distinct from the background while keeping text ≥4.8:1 (AA).
Before the fix, `surfaceContainerLow` (1.22:1 vs background) was below the 1.5:1 elevation
perception threshold — cards appeared flat against the background.

Design tokens: primary teal `#00695C` (light) / `#80CBC4` (dark), cream `#FAF9F6` /
sepia `#3E2723` backgrounds, serif headers, monospace editor, sans-serif body.
All color slots defined per Material 3 spec (primary, secondary, tertiary, error,
surface layers, outline, inverse, scrim).

## How the calculator works
- Plain-text note body. Each line is parsed by `NoteProcessor`:
  - `Milk 12.5`, `200 + 15%`, `grocery 1000 * 2` → added to the running sum
  - `rent = 1500` → assignment (also added unless the label is a total keyword)
  - `total` / `sum` / `subtotal` / `grand` / `grandtotal` / `Σ` / `σύνολο` → total keyword (closing marker, not added)
- Pratt parser in `calculator/` evaluates expressions; percent is "percent-of" semantics.
- Full function set: `^`, `sqrt`, `sin`/`cos`/`tan`, `log`/`ln`, `round`/`abs`/`min`/`max`, `π`, `e`.

## Editor: total readout (key UX decision)
The computed total is **not stored inside the editable text**. When the last non-empty line is a
total trigger (`total` or `total =`), a separate non-editable `Text` renders the amount as an
indented sub-line under the keyword:

```
total
       = 6000
```

- The number is always live, derived from `EditorViewModel.UiState.currentSum`.
- Typing `total` shows the readout immediately — no auto-insert of `=`, no Calculate button.
- Because the text field is never rewritten during typing, the keyboard never resets.
- On save, only `total =` (the trigger form) is persisted; the number is recomputed on reopen.

## Navigation & back behavior
- `Navigator` (Phase 1): Home / Editor / Settings / About, pop returns to Home.
- `EditorScreen` installs a `BackHandler`: swipe-back, hardware back, and the arrow button all
  **save then return Home**. A completely empty note (zero characters, no title) is discarded;
  anything else (even only whitespace) is saved.

## Project layout
```
composeApp/src/commonMain/kotlin/com/aritiq/calcnote/
  calculator/      Pratt parser + evaluator + tests
  domain/          Note, NoteProcessor (text→sum), NoteProcessorTest
  data/            repository/ (NoteRepository, SQLDelight impl), db/ (driver), sqldelight/ (schema)
  ui/
    editor/        EditorScreen, EditorViewModel
    home/          HomeScreen, HomeViewModel
    settings/      SettingsScreen, SettingsViewModel
    navigation/    Navigator, Route
    theme/         AritiqTheme, PaperComponents (grain overlay), editorTextStyle
```

## Build & test
Set the JDK to Android Studio's bundled JBR, then:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest :composeApp:assembleDebug
```
Install the debug APK on a device to test the editor UX (keyboard stability, swipe-back save).

---

# Roadmap

Shipped features marked **✅**, items below are ordered within each phase.

## ✅ Fix — Dark mode
Persisted theme setting now loads on cold start so System/Light/Dark works reliably.

### ✅ Export / Import (JSON / CSV)
Single note (share icon in editor), selected notes (long-press multi-select on Home), or
bulk (Settings). JSON preserves full schema (`id`, `title`, `content`, timestamps,
`folderId`, `tags`, `isPinned`, `isArchived`, `favorite`, `drawing` for forward compat).
CSV is `title, content` flat format. Import supports MERGE (skip duplicates by id) or
REPLACE (wipe and load).

---

## Phase 2 — Polish & Data Portability

### ✅ About screen
App: Aritiq · Version: from `BuildConfig.VERSION_NAME` · Developer: HNatividad. Shown at the bottom of Settings.

### ✅ View Options
Home list layout selector via `MoreVert` dropdown next to the Settings icon. Four modes:
- **Simple list**: compact rows — title only
- **Detailed list**: rows with title, preview, date (default)
- **Small grid**: 2-column card grid
- **Large grid**: 1-column cards with full preview
Persisted via setting table. Dropdown stays open on selection so the checkmark is visible.

### ✅ Sorting / Display per month
Sort and month-group in the MoreVert dropdown (same one as view modes, separated by a divider). Sort options:
- **Newest first** (default) — `createdAt DESC`, month-grouped
- **Oldest first** — `createdAt ASC`, month-grouped
- **Recently edited** — `updatedAt DESC`, month-grouped
- **Alphabetical** — `title ASC`, flat list
List modes show month headers (`January 2026` etc.) for date-based sorts. Grid modes sort but skip month headers. Persisted via setting table.

### ✅ Search UI
Search bar at the top of Home screen wired to `repo.searchByText`. Searches both `title` and
`content` in real time.

---

## Phase 3 — Organization & Security

### Folders
Create, rename, delete folders. Assign a note to a folder via a picker in the editor toolbar.
Browse notes by folder on Home (section header or drawer). Schema (`folder` table) already in place.

### Notes Tags
Create tags, assign them to notes, filter the Home list by tag. Schema (`tag` + `note_tag` tables)
already in place.

### ✅ Archive / Trash
Swipe left on a note in list mode to archive (archive background slides in). Archived section at the bottom of the Home list — tap to expand, shows "Restore" and "Delete" actions. Delete shows a confirmation dialog ("Delete note? This cannot be undone.") before permanent removal. `selectArchived` query fetches archived notes. Grid modes show archived section below the grid.

### Folder-level lock
A folder can be locked. Locked folders are hidden from the Home list until the user authenticates
with biometrics (fingerprint / face) or a PIN code.

- Lock toggle: long-press folder → "Lock with biometrics".
- When locked: folder contents are **hidden** from Home, search, and recent lists.
- Unlock: tapping the locked section triggers `BiometricPrompt` (Android) or `LocalAuthentication` (iOS).
- Once authenticated, the folder is visible until the app goes to background.
- No encryption-at-rest for note content — just visibility gating. Encryption is a future enhancement.

---

## Phase 4 — Handwritten Notes

### Goal
User draws or hand-writes inside a note using finger or stylus. Strokes are stored and replayed
on open. No handwriting-to-text conversion.

### Storage
New `drawing` column in `note` table (nullable `TEXT` — JSON blob of strokes).

```
data class Stroke(
    val points: List<Point>,   // x, y, timestamp, optional pressure
    val color: Long,           // ARGB color
    val width: Float,          // stroke thickness
)
```

Serialized with `kotlinx.serialization`. A sketch-only note has empty `content` + populated `drawing`.

### Editor integration
- Toggle in the editor top bar switches between **text mode** (`BasicTextField`) and **draw mode** (full-screen `Canvas`).
- Draw mode renders the same ruled-paper background and margin line.
- Toolbar in draw mode: pen color picker, thickness slider, undo last stroke, clear all, back to text.
- On save: serialize strokes → JSON → write to `drawing` column.
- On open: if `drawing` is non-null, show strokes on `Canvas` in replay or editable mode.

### Out of scope (Phase 4)
- Handwriting → text OCR.
- Pressure sensitivity beyond raw capture (passthrough from stylus API).
- Palm rejection tuning.
- Pinch-to-zoom canvas.
- Hybrid text+drawing on the same view (either/or per note for v1).

---

## Deferred
Items from the original spec and future enhancements that are not yet scheduled:

- Multi-section subtotals (`subtotal` then `total` blocks in one note)
- Deeper back-stack in `Navigator`
- Spreadsheet mode (turn a note into a table with auto-totals)
- Receipt mode (local OCR extraction)
- Templates (budget, shopping, invoice, meeting notes)
- Custom formulas (`subtotal`, `tax`, `discount`, reusable user-defined)
- Cross-note references (`Budget.Remaining`)
- Offline charts (pie, bar from numeric data)
- Markdown rendering in editor
- Keyboard shortcuts (tablets / foldables)
- Local encryption (PIN / biometric with encrypted storage)
- Plugin-ready calculator engine (engine already cleanly separated)
- iOS target (add `iosMain` + platform driver)

---

### Save point — 2026-07-16
- 59 unit tests (Calculator 21, NoteProcessor 29, SqlDelightNoteRepository 9)

### Save point — 2026-07-17
- 70 unit tests (+6 ExportService, +5 ImportService)
- Export/Import: single (editor share icon), selected (long-press multi-select on Home), bulk (Settings)
- JSON export preserves full schema (including tags, drawing forward-compat)
- CSV export: `title, content` with proper escaping
- Import: MERGE (skip duplicates) or REPLACE (wipe then load). Auto-detects JSON vs CSV by content prefix.
- FileProvider + share sheet for file export; OpenDocument for file import (accepts `application/json` + `text/*`)
- Import fixes: BOM-stripping for Android content-resolver streams, dialog stays open during import with progress indicator, file-read errors shown as result text instead of silent catch
- `selectAll` query added to Note.sq; `all()` + `tagsForNote()` on NoteRepository
- `ExportService`, `ImportService`, `ExportModels` (serializable DTOs) in `data/export/`
- App renamed CalcNote → Aritiq
- Pin crash fixed: `Surface(onClick=)` → `Modifier.clickable` to avoid gesture conflict with ListItem
- Flow subscription leak fixed: `recentJob?.cancel()` before new `launchIn` in `load()`
- Pin duplicate-key crash fixed: `selectRecent` excludes pinned notes (`WHERE is_pinned = 0`)
- Delete confirmation dialog added to EditorScreen
- Archived section no longer overlaps FAB (80dp bottom padding on lists + grid modes)
- All Phases 1–4 above are ordered by priority and ready for implementation.
