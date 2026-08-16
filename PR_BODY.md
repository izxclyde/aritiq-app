## Summary

Implements the Phase B (UX polish) and Phase C (feature surfacing) items from the UI
improvements tracker, adds an accent theming system, fixes biometric unlock crashes,
and cleans up dead code. All builds pass (`assembleDebug`).

---

## Accent Theming System

- **3 notebook accent palettes** (Teal, Orange, Purple) that swap primary/secondary/tertiary
  color groups while keeping the paper background/surface identity constant (`Theme.kt`).
- Accent is **persisted** in the settings table and applied on app startup (`SettingsViewModel`,
  `App.kt`).
- **Accent picker** added to Settings screen with circular swatches that adapt to
  light/dark mode (`SettingsScreen.kt`).
- **Bug fix**: Removed `load()` call from `SettingsScreen`'s composition block — it was
  racing with `setAccent()` and reverting the accent before the DB write committed.

## Button Color Consistency

All accent-colored components now use the `primary` color family consistently:
- **FABs** (Home + Manage Folders): explicit `primary`/`onPrimary` instead of the default
  `primaryContainer` which appeared washed out.
- **Folder filter chips**: explicitly set `primary`/`onPrimary` for selected state — was using
  M3 default `secondaryContainer` which showed the *opposite* accent color.
- **EmptyState action button**: switched from `FilledTonalButton` (uses `secondaryContainer`) to
  `Button` (uses `primary`) so "Create note" and "Create folder" match the FAB.

## UX Polish (Phase B)

- **Shared `EmptyState` component** (`ui/components/EmptyState.kt` new file): icon + title +
  optional action button. Wired into HomeScreen (all 4 view modes + search empty state),
  ManageFoldersScreen.
- **Date shown** in detailed list rows via `formatDate` helper (`HomeScreen.kt`).
- **Select-all button** (`Icons.Filled.SelectAll`) in multi-select toolbar; `selectAll()` now
  includes `searchResults` when a query is active.
- **Search results respect view mode**: dispatches on `state.viewMode` (simple/detailed/grid)
  instead of always showing detailed list.
- **Star/favorite toggle** in `NoteRowDetailed` trailing content + `HomeViewModel.toggleFavorite()`
  wired to existing `setFavorite` repo method.
- **Dropdown menus**: replaced manual `Row` checkmark layout with proper `leadingIcon`/`trailingIcon`
  slots.
- **Archived section header**: replaced Unicode arrows with Material `KeyboardArrowUp`/`Down` icons.
- **Swipe-to-archive**: added `Archive` icon alongside text.

## Feature Surfacing (Phase C)

- **Favorite toggle** visible directly in note list rows (detailed view).
- Manage Folders: Locked folder icon changed from `Filled.Lock` to `Outlined.Lock` for visual
  distinction.

## Bug Fixes

- **BiometricPrompt crash** (`LockManager.kt`): `setNegativeButtonText("Cancel")` is now gated
  on `Build.VERSION.SDK_INT < 30` to avoid `IllegalArgumentException` when `DEVICE_CREDENTIAL`
  is allowed (API 30+). Authenticator flags now use `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`
  on API 30+.
- **Biometric retry**: `onAuthenticationFailed()` no longer shuts down the prompt — user can
  retry biometric after a failed attempt.
- **PaperGrainOverlay** moved from per-screen (EditorScreen) to single global instance in
  `App.kt`; now uses `MaterialTheme.colorScheme.outline` instead of hardcoded warm-tone palette,
  so it reads correctly on both light and dark backgrounds.

## Cleanup

- Removed dead `Route.About` from navigation (`Navigator.kt`, `App.kt`).
- Theme renamed from `CalcNoteTheme` to `AritiqTheme`; typography now loads bundled fonts
  (Playfair Display, Inter, JetBrains Mono) via Compose Resources instead of system font
  families.
- App icon assets added (`drawable/ic_launcher_foreground.xml`, `mipmap/ic_launcher.xml`,
  `values/colors.xml`).
- Android theme renamed to `Theme.Aritiq.Starting`.
- `build.gradle.kts`: added `group`, fixed missing trailing newline.

## Files Changed

| File | Change |
|------|--------|
| `Theme.kt` | Accent enum, light/dark color scheme extensions, bundled fonts |
| `App.kt` | Accent propagation, grain overlay, removed Route.About |
| `HomeScreen.kt` | EmptyState, date display, favorite toggle, select-all, search view modes, chip/FAB colors |
| `HomeViewModel.kt` | `toggleFavorite()`, `selectAll()` search-aware, `LockManager` guard |
| `SettingsScreen.kt` | Accent picker, removed `load()` race, website link fix |
| `SettingsViewModel.kt` | Accent state + persistence |
| `ManageFoldersScreen.kt` | EmptyState, FAB colors, Outlined.Lock |
| `LockManager.kt` | API 30+ credential fallback, retry on failure |
| `Navigator.kt` | Removed `Route.About` |
| `EditorScreen.kt` | Dropdown checkmark icons, removed per-screen grain overlay |
| `PaperComponents.kt` | Theme-aware grain color |
| `EmptyState.kt` | New shared component |
| `AppModule.kt` | `LockManager` added to `HomeViewModel` DI |
| `build.gradle.kts` | group, trailing newline |
| `AndroidManifest.xml` | App icon, theme rename |
| `LockedFolderScreen.kt` | Outlined.Lock icon |
| `README.md` | Documented device credential fallback |
