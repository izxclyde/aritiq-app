# Aritiq — End-to-End Analysis & Enhancement Report

**Date:** 2026-09-05 · **Commit analyzed:** `34a23e3` (main, includes `ff30b8a` "fix: secure locked note export and import") · **Version under test:** 0.1.0 (debug APK, versionCode 1)

**Scope:** build + unit tests, first-ever lint run, live E2E smoke test on the attached emulator, and a code-level review of the crypto/export/lock paths. Report only — no code was changed.

---

## 1. Executive summary

The app is in solid shape for an early-stage project: **all 77 unit tests pass**, the build is green, core architecture is clean (MVVM + repository + SQLDelight + Koin), and the recently reworked export/import crypto uses textbook-correct AES-GCM + PBKDF2 (310k iterations). On-device, navigation, dialogs, settings, theming, and the biometric locked-folder auth flow (including cancellation) all behaved correctly with zero crashes across the whole session.

The highest-value findings:

1. **Confirmed state bug:** after clearing the export password, `Settings` still reports "password set" after an app restart (`SettingsViewModel.kt:33` vs `:62`).
2. **E2E blocker (environment, not app):** text input into any Compose text field does not work on the API 37.1 preview emulator (Android 17 beta) — Compose never establishes the IME input session (`EditorInfo` stays empty, `inputType=0`). System View-based apps type fine on the same emulator. Text-dependent flows (notes, search, passwords) must be re-verified on a stable API (e.g. 36) image.
3. **`lintDebug` currently fails** on one Compose-Multiplatform lint false positive, plus 25 warnings (mostly dependency updates) — lint can't be used as a CI gate until this is triaged.
4. **Security posture is honest but thin:** the locked folder is a UI gate only (notes are plaintext in SQLite), and the stored export-password hash is unsalted SHA-256. Both are documented as deferred in the README — the roadmap below prioritizes them.
5. **Process gap:** no PR CI exists — tests and lint never run on pull requests; release builds have R8 disabled.

---

## 2. Build & unit tests — PASS

`./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest :composeApp:assembleDebug` → **BUILD SUCCESSFUL**.

| Suite | Tests | Failures |
|---|---|---|
| `CalculatorTest` (parser/evaluator) | 21 | 0 |
| `NoteProcessorTest` (line parsing) | 29 | 0 |
| `ExportServiceTest` (JSON/CSV export) | 8 | 0 |
| `ImportServiceTest` (merge/replace/CSV edge cases) | 10 | 0 |
| `SqlDelightNoteRepositoryTest` (JdbcSqliteDriver integration) | 9 | 0 |
| **Total** | **77** | **0** |

- The "test suite red" note in `UI-IMPROVEMENTS.md` (2026-08-16) is **stale** — the commonTest fakes were completed by `ff30b8a` and the suite is fully green. The doc should be updated or the note removed.
- Frameworks in use: `kotlin.test` + `kotlinx-coroutines-test`, hand-written fakes. **No instrumentation (`androidTest`) source set exists** — the biometric/back-handler/editor flows have no automated UI coverage.

## 3. Lint — first run, 1 error + 25 warnings

`./gradlew :composeApp:lintDebug` → **BUILD FAILED** (lint errors fail the task by default).

- **Error (false positive):** `MainActivity.kt:30` — `RememberReturnType` claims `remember { Navigator() }` returns Unit. It doesn't: `Navigator` is a plain class, the file compiles, and navigation demonstrably works at runtime. This is a Compose-Multiplatform lint resolution issue. Fix by disabling/baselining the check (`lint { disable += "RememberReturnType" }` in `composeApp/build.gradle.kts`) so lint can gate CI.
- **Actionable warnings:**
  - `AndroidManifest.xml:11` — `android:allowBackup` is deprecated on Android 12+; add `android:dataExtractionRules` (also relevant: the app deliberately sets `allowBackup=false`, so an explicit extraction-rules XML keeps that intent valid on modern Android).
  - `AndroidManifest.xml:28` — redundant label.
  - `UpdateChecker.android.kt:87` — use KTX `String.toUri`.
- **Noise:** ~20 "newer version available" warnings (Kotlin 2.2.20→2.4.10, Compose 1.8.2→1.12.0, SQLDelight 2.1.0→2.3.2, Koin 4.0.0→4.2.2, coroutines 1.10.2→1.11.0, AGP 8.11.0→8.11.2/9.4.0). Worth batching after the release pipeline stabilizes — note Compose upgrades are also the most likely fix for finding #4.2 below.

## 4. E2E smoke test on emulator (API 37.1 / Android 17 beta, x86_64)

### 4.1 What passed ✅

| Flow | Result |
|---|---|
| Install + cold launch (splash → Home) | ✅ (first frame ~18s on debug/emulator — measure release before worrying) |
| Home layout: search bar, folder chips (`All`/`Try`), Locked row, FAB, empty state | ✅ |
| Update checker (only network feature) | ✅ live — detected the published GitHub release and showed the dialog; "Later" dismisses correctly |
| Navigation: Home → Editor → Settings → back (incl. hardware back) | ✅ |
| Editor screen: ruled-paper canvas, `Σ` live-sum status bar, word/char counts, folder/trash toolbar | ✅ renders correctly |
| Locked folder auth: tap → `BiometricPrompt` invoked with `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL`; system fell through to device-credential (fingerprint ineligible) | ✅ |
| Auth cancellation (negative test): back out of the credential screen | ✅ app stays on Home, remains locked, no crash, no forced navigation |
| Settings: theme System/Light/Dark + accent palettes apply live and consistently app-wide (verified Dark + Purple, then restored Light + Orange) | ✅ |
| Crash monitoring (logcat crash buffer + FATAL/ANR scan) across the entire session | ✅ zero crashes, zero ANRs |

### 4.2 What couldn't be tested — text input is broken on the API 37.1 preview ⛔

No keystroke reaches any Compose text field on this emulator (editor body, title, Home search — all fail; the note I attempted was correctly discarded as empty). Evidence chain:

1. Tapping a field **does focus it** (M3 focus border + cursor render).
2. `dumpsys input_method`: the app becomes the IME client but `EditorInfo` stays **empty (`inputType: 0`)** and `mInputShown=false` — Compose never establishes the input session, so no IME appears and injected keys have no target.
3. Control test: typing works fine in the **system Settings app** (View-based `EditText`) on the same emulator with the same injection method.
4. The app's field code is bog-standard Material3 `TextField`/`BasicTextField` — nothing app-side could produce an empty `EditorInfo`.
5. Gboard runs in floating/collapsed mode on this image (also during the passing system-app test, so it's a red herring); forcing `show_ime_with_hard_keyboard=1` didn't change anything; no alternative IME is installed.

**Attribution:** Compose 1.8.2 ↔ Android 17 (API 37.1, `sdk_gphone16k`) preview-platform incompatibility, most likely. The developer's own prior data (`Try` folder) proves text input worked here before — consistent with a recent preview-image change. No public issue tracker match found yet; worth filing on the [Android 17 feedback tracker](https://developer.android.com/about/versions/17/feedback) if it survives a Compose upgrade.

**Recommended follow-up:** create an AVD on the locally-available stable `android-36` image (it currently exists only as a stub download) and re-run the text-dependent flows: note create/edit/total readout, search, folder rename, export/import with password (including reproducing bug B1 below live).

## 5. Confirmed bugs (code-level)

| # | Severity | Where | Description |
|---|---|---|---|
| B1 | Medium | `SettingsViewModel.kt:33` vs `:62` | `load()` computes `passwordSet = containsKey("export_password_hash")`, but `clearExportPassword()` writes `""` instead of removing the key. After clearing the password and restarting, Settings shows the "password is set" UI (change/remove) while the export flow (`isPasswordSet()`, which checks blankness) treats it as unset. Fix: remove the key on clear, or make `load()` check blankness like `isPasswordSet()` does. |
| B2 | Low | `SettingsViewModel.kt:50,55,104-106` | Custom `runBlocking` wrapper blocks the calling (main) thread inside `setExportPassword`/`changeExportPassword`. Work is small (settings write), but it's an ANR foot-gun; make the functions `suspend` or launch into the existing scope. |
| B3 | Low | `SettingsScreen.kt:196` | Stale copy: "No password set. Exports use default password." — verified live on-device. There is no default password anymore (per-export prompt is mandatory); the text misinforms users about export security. |
| B4 | Low | `ExportService.kt:28,48`, `ImportService.kt:82` | CSV export/import functions have **no UI call sites** (tests only) and are now semantically inconsistent with JSON: CSV still excludes locked notes while JSON includes them. Decide: wire up or delete. |
| B5 | Cosmetic | `EncryptionService.android.kt:30-41` | `decryptImpl` doesn't validate minimum input length; truncated files throw from slicing and surface as "Wrong password" (handled, but the message is misleading for corrupt files). |
| B6 | Tooling | lint report | `RememberReturnType` false positive fails `lintDebug` (see §3) — blocks lint-as-CI-gate until suppressed. |

## 6. Security assessment (export/lock paths)

**Solid:**
- Export crypto is textbook-correct: AES-256-GCM, random 12-byte IV per export, random 32-byte salt, PBKDF2WithHmacSHA256 @ 310,000 iterations (`EncryptionService.android.kt`). The post-`ff30b8a` flow always uses a user-typed password; the hardcoded `4r1t1q-4pp` fallback and plaintext export path are gone.
- Decryption failures are caught and surfaced as "Wrong password" — no crash path; tampering fails via GCM tag verification.
- Locked notes are excluded from Home/search/pinned/archived queries; app auto-locks on `ON_STOP`; `allowBackup=false`.

**Thin (matches README's "deferred" list — prioritized here):**
- **Locked folder is a UI gate, not encryption.** Locked notes live in plaintext in `calcnote.db`; anyone with ADB backup access or a rooted device reads them regardless of biometrics. → P1: AndroidKeyStore-wrapped key (StrongBox when available) encrypting either the DB (SQLCipher) or locked-note content at the repository layer.
- **Stored export-password hash is unsalted SHA-256** (`hashPasswordImpl`) with no attempt limiting. It's only a convenience check on-device, but salting it (or replacing with a keystore-backed key) is cheap. → P2.
- **Update/security dependency on one self-hosted runner** holding the release keystore; losing it ends signed updates for installed users. → P1: export/backup the keystore securely, or move to Play App Signing-style management.
- `REQUEST_INSTALL_PACKAGES` is required for the self-update path — unavoidable for sideload distribution, but worth documenting for users.

## 7. Enhancement roadmap (prioritized)

### P0 — correctness & pipeline (small, high value)
1. Fix B1 (`passwordSet` state bug) and B2 (`runBlocking`), update B3 text — three tiny diffs.
2. **Add PR CI** (`.github/workflows/ci.yml`): build + `testDebugUnitTest` + `lintDebug` (with the `RememberReturnType` false positive disabled/baselined). Today nothing tests PRs; tests have never run in CI.
3. Enable R8 minification + resource shrinking for release (`isMinifyEnabled=false` today) with keep rules; the release APK ships unshrunk.
4. Add `dataExtractionRules` XML (lint warning; keeps `allowBackup=false` intent explicit on Android 12+).

### P1 — testing, robustness, architecture
5. **E2E re-test on stable API 36** image; then keep API 36 as the CI/local device baseline instead of the 37.1 preview (see §4.2).
6. First instrumentation tests (`androidTest`): editor save-on-back, locked-folder auth gating, export password dialogs — the flows unit tests can't reach.
7. Crypto regression tests in `androidUnitTest` against the real `EncryptionService.android` impl: round-trip, wrong password, truncated file, magic-header sniffing.
8. Replace the custom `Navigator` (mutable list, no config-change survival, no deep links — acknowledged in its own KDoc) with Navigation-Compose or Voyager.
9. Route the screens' direct `koinInject<NoteRepository>()` usage (Home/Editor/LockedFolder) through ViewModels; make `SettingsViewModel` a Koin `factory` (it's a `single` today); type `rememberExportWithPassword(context: Any)` as `Context` (`ExportPasswordDialogs.kt:105`).
10. Fix the `NoteProcessor` Phase-2 gap documented in its header: editing a number above an already-filled `total = …` line doesn't refresh the readout.

### P2 — features & polish
11. Tags UI (schema + queries already exist, unused); or remove from schema until Phase 2 actually lands.
12. CSV: wire into UI (e.g. "Export as spreadsheet") or delete the dead functions; if kept, align locked-note behavior with JSON.
13. Undo snackbar for deletes (already on the README roadmap).
14. Auto-lock timeout setting (currently locks on every `ON_STOP`), and a "check updates only on Wi-Fi" toggle.
15. Batch dependency updates (Kotlin 2.4.x, Compose 1.12, SQLDelight 2.3.2, Koin 4.2) — also the likeliest fix for the API 37 input issue.
16. Housekeeping: drop the stale red-test note from `UI-IMPROVEMENTS.md`, remove committed `PR_BODY.md` from the repo root.

---

## Appendix — method & artifacts

- Build/tests/lint: Gradle 8.14 wrapper, Android Studio JBR (JAVA_HOME), `--rerun-tasks` forced test run; test XMLs in `composeApp/build/test-results/testDebugUnitTest/`.
- Emulator: `emulator-5554`, API 37.1 (`sdk_gphone16k_x86_64`, Android 17 beta), 1080×2400; debug APK `Aritiq-0.1.0-debug.apk` streamed-install; flows driven via `adb input` + `screencap`; screenshots in `%TEMP%\aritiq-e2e\` (`01-…` through `25-…`); crash/ANR monitoring via logcat crash buffer.
- Code review files: `SettingsViewModel.kt`, `EncryptionService.android.kt`, `Navigator.kt`, `MainActivity.kt`, `EditorScreen.kt`, `HomeScreen.kt`, `SettingsScreen.kt`, `ExportPasswordDialogs.kt`, `ExportService.kt`, `ImportService.kt`, `NoteProcessor.kt`, `AndroidManifest.xml`.
- Theme/accent settings were restored to their original values (Light/Orange) after testing.
