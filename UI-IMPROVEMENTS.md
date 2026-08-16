# UI Improvements — Progress Tracker

UX analysis of Aritiq (offline notes + calculator) and the implementation plan.
Phase A (themes & icons) is complete. Phases B/C are scoped but not started.

Status legend: `[ ]` pending · `[~]` in progress · `[x]` done · `[!]` blocked/deferred

---

## Phase A — Themes & icons (visual polish, no behavior change)

| # | Item | Files | Status |
|---|------|-------|--------|
| A1 | App icon (adaptive + vector fallback + monochrome) & Android 12 splash | `AndroidManifest.xml`, `res/mipmap*`, `res/drawable/ic_launcher*`, `res/values(-night)/themes.xml`, `res/values/colors.xml` | `[x]` |
| A2 | Bundled OFL fonts (serif display / mono editor / body sans) via Compose resources | `composeResources/font/*.ttf`, `ui/theme/Theme.kt`, `composeApp/build.gradle.kts` | `[x]` |
| A3 | Notebook accent themes (Teal default / Orange / Purple) persisted in settings | `ui/theme/Theme.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `ui/App.kt` | `[x]` |
| A4 | Paper grain on all surfaces + theme-aware grain color | `ui/theme/PaperComponents.kt`, `ui/App.kt`, `ui/editor/EditorScreen.kt` | `[x]` |
| A5 | Icon consistency pass (locks, archive, expanders, checkmarks, select-mode bar) | `ui/home/HomeScreen.kt`, `ui/editor/EditorScreen.kt`, `ui/folders/ManageFoldersScreen.kt`, `ui/home/LockedFolderScreen.kt` | `[x]` |
| A6 | Rename CalcNote → Aritiq theme symbols | `ui/theme/Theme.kt`, `ui/App.kt` | `[x]` |

### Verification (gate)

- [x] `gradlew :composeApp:compileDebugKotlinAndroid :composeApp:assembleDebug` succeeds
- [x] No unit-test changes required (pure visual — no logic touched)

---

## Phase B — UX polish (in progress)

- [x] Empty states with icon + CTA (Create note / Create folder)
- [ ] Undo snackbar for delete/archive/restore
- [x] Date shown in detailed list rows (README promises it; currently missing)
- [x] Select-all button in multi-select bar; drop redundant Close
- [x] Fix dead `Route.About -> HomeScreen` mapping
- [x] Respect chosen view mode in search results

## Phase C — Feature surfacing (in progress)

- [ ] Tags UI (schema + `Tag` model ready, never surfaced)
- [x] Favorite/star toggle (`favorite` field is dead in `Note` model)
- [ ] RTL ruled-paper margin fix + font-scaling (accessibility) verification

---

## Changelog

> Log every completed change here (date · item · summary) so progress is auditable.

### 2026-08-16 (Phase B1–B5 + C1)
- B1: Shared `EmptyState` (icon + title + CTA); Home (all 4 view modes), search ("No results"), and Folders ("No folders yet" → create dialog) now show it.
- B2: Detailed list rows show `createdAt` date (`August 16`, matches month-group headers; `formatDate` helper).
- B3: Select-all (`Icons.Filled.SelectAll`) added to the select-mode bar; `selectAll()` now covers search results too.
- B4: Removed dead `Route.About` object and its `HomeScreen` mapping.
- B5: Search results render per chosen view mode (simple/detailed/grid cards) instead of always detailed list.
- C1: Star/favorite toggle in detailed rows; `HomeViewModel.toggleFavorite` (schema + `setFavorite` query already existed).
- Gate: `:composeApp:compileDebugKotlinAndroid` + `:composeApp:assembleDebug` pass. `:composeApp:testDebugUnitTest` is red on HEAD too — pre-existing: `commonTest` mocks don't implement `selectByFolderExcluding` / `getLockedFolder` / `ensureLockedFolderExists` added by the locked-folder work (not part of this task).

### 2026-08-16
- Init tracker with Phase A plan.
- A1: Notebook app icon (teal Σ on cream ruled-paper, adaptive + monochrome + legacy vector) and `Theme.Aritiq(.Starting)` paper themes wired into the manifest.
  Note: v31 splash styles were dropped — this compileSdk's android.jar no longer exposes `android:postSplashScreenTheme` (moved to `core-splashscreen`); Android 12+ shows the OS splash with the new launcher icon and the app paints cream paper via `windowBackground`.
- A2: Bundled Inter / JetBrainsMono / PlayfairDisplay TTFs via `composeResources/font/`; pinned `group = "com.aritiq.calcnote"` in build.gradle.kts; wired via `FontFamily(Font(Res.font.*))` (CM 1.8.2 API — no `FontFamily(FontResource)` overload).
- A3: `NotebookAccent` Teal/Orange/Purple color schemes + settings picker persisted under `"accent"`.
- A4: App-wide `PaperGrainOverlay` using `colorScheme.outline`; grain on Home/Editor surfaces.
- A5: Outlined vs Filled lock semantics (passive vs action), `KeyboardArrowUp/Down` archive expander, `leadingIcon` Check dropdowns, archive swipe now icon + label, select-mode Close removed.
- A6: `CalcNoteTheme` → `AritiqTheme`.
- Gate: `:composeApp:compileDebugKotlinAndroid` + `:composeApp:assembleDebug` pass (19.2 MB debug APK).
