# Aritiq — Product, Functionality & UI Enhancement Plan

**Date:** 2026-09-05 · **Companion to:** `FINDINGS.md` (E2E analysis) and `UI-IMPROVEMENTS.md` (work tracker)

---

## 0. Product framing

Aritiq is a **plain-text notes app with a calculator built in** — "notes that can do math." The calculator is an accelerator, never a requirement: most notes are just notes, and users are never expected to add numbers or totals. The math features serve the note, not the other way around.

Rules every enhancement must pass:

1. **Protect the sacred path** — open → write (math optional) → leave. Zero added friction.
2. **Privacy is the brand** — no accounts, no cloud, no analytics SDKs; "sync-like" needs are met locally (encrypted export, auto-backup to a user-owned folder).
3. **Never assume numbers** — every UI surface must look right for a note that is pure prose. Calculator metadata appears only where a total actually exists.

## 1. Current-state gaps (observed / reported)

- **Editor ruled-line drift (reported)** — on long notes the paper rules drift away from the text. Root cause verified in code: rules are drawn at a fixed `24.sp.toPx()` interval anchored at a magic `× 0.75f` factor (`EditorScreen.kt:184-199`), while each text line box rounds its height independently per line — the fractional-px error accumulates line over line (density/font-scale dependent).
- Note cards show **title only** (`NoteRowSimple`) — no content preview; for a notes app the card should show what's inside.
- Update dialog re-prompts on every Home re-entry (observed in E2E).
- No first-run sample/onboarding for the calc grammar (those who want math must read the README).
- Locked folder is a UI gate (plaintext DB), no auto-lock timeout setting.
- Tags: schema + export support exist, zero UI.
- Known parser gap: editing a number above a filled `total =` line doesn't refresh the readout.

## 2. Product enhancements

### P1 — Better cards & calculator visibility (conditional, never assumed)

1. **Card content preview** — title + one-line content snippet (+ date on simple rows); the standard pattern for notes apps, serves pure-prose notes first.
2. **Conditional Σ chip** — when (and only when) a note has a computed total, show a small accent Σ chip on the card. Plain notes render as plain notes; calc notes get glanceable totals. No monthly/aggregate sums.
3. **Copy total** — tap the editor's Σ badge to copy the note's total to the clipboard (relevant only in calc notes; invisible otherwise).

### P2 — Teach the grammar (opt-in)

4. **First-run sample note** — seed one editable "Try me" note showing both plain text and the calc lines (`label amount`, `name = expr`, `total`); deletable like any note, no schema change.
5. **Syntax cheat sheet** — long-press the Σ badge / Help entry opens a paper-styled sheet listing the line grammar with live-editable examples.

### P3 — Calculator depth (power users, optional)

6. **Labeled subtotals / multi-total notes** — `total food`, `total rent` produce a labeled readout per keyword occurrence; bare `total` stays the grand total. A pure calculator-feature upgrade: parser + readout redesign + tests; schedule as its own mini-project.

### P4 — Trust & data safety

7. **Encryption-at-rest for the locked folder** — AndroidKeyStore-wrapped key (StrongBox when available) encrypting locked-note content at the repository layer (or SQLCipher for the DB). Today the biometric gate is UI-only; this makes "locked" mean something under ADB/root.
8. **Auto-lock timeout** setting: immediately / 1 min / 5 min (settings key via `SettingsRepository`), plus lock-on-app-switch behavior tied to it.
9. **Auto-backup to a user folder (SAF)** — scheduled (weekly/on-change) encrypted `.aritiq` export into a user-chosen directory. Converts the existing export feature into a device-loss safety net without any cloud.
10. **Update-check cadence** — check at most once/day, persist dismissal; never re-prompt within a session (fixes the re-prompt observed in E2E). Settings toggle "Check for updates".

### P5 — Reach (later, after P1–P4 land)

11. **Home-screen widget** — pinned note's total + a "+" quick-capture shortcut (needs Navigator deep-entry via intent extra).
12. **Share-in capture** — register as a share target; received text becomes a new note.
13. **Material You dynamic color** as a fourth accent option; **iOS target** once Android core stabilizes (KMP-ready already).
14. **Deliberately deferred** (revisit only on user demand): spreadsheet grid mode, OCR receipts, accounts/sync, templates marketplace.

## 3. Functionality work (enablers & fixes)

| Item | Notes |
|---|---|
| **Fix editor ruled-line drift** | Draw rules from the text layout's real geometry: capture `onTextLayout`, compute the actual baseline pitch and first-baseline offset, and pass those into `drawBehind` (single source of truth; immune to density rounding and font scale). Calibrate the red margin line and the `= total` readout sub-line to the same rhythm. Verify with a 100+ line note at 1.0x and 1.3x font scale. |
| Tags UI | Schema, `tagsForNote()`, and export fields already exist. Editor tag chips + home filter row combined with folder/search. Completes the tracked Phase-C item. |
| NoteProcessor total-refresh | Editing a number above an already-filled `total =` line doesn't refresh (documented Phase-2 gap in its header). Core-loop correctness. |
| Smarter search | Match bare amounts (`1500` finds `rent = 1500`); search locked notes only while unlocked. |
| Import error clarity | Distinguish "wrong password" from "corrupt/truncated file" (possible after the B5 fix) instead of one generic message. |
| Undo snackbar | For delete/archive/restore (already on the Phase B list). Data-loss anxiety is the #1 retention killer in notes apps. |
| Navigator → real navigation lib | Needed before widgets/deep links (P5); acknowledged in `Navigator.kt`'s own KDoc. |
| Localization | UI strings are hardcoded English; total keywords already accept `Σ`/`σύνολο` — i18n is half-anticipated. |

## 4. UI/UX enhancements

**Editor**
- Subtle syntax rendering: recognized amounts get a faint accent underline; `total` lines get a soft highlight. Keep the paper aesthetic — tint, not color blocks.
- Keyboard accessory row (above IME): `=`, `+`, `total` quick-insert keys for one-thumb capture.
- Σ badge: tap = copy total (P1.3), long-press = cheat sheet (P2.5); readout shows item count ("Σ 1,516.25 · 8 items").

**Home**
- Card redesign (simple/detailed/grid variants): title + content snippet + date; conditional Σ chip (accent-colored); grid cards show the chip prominently when present. Detailed row keeps favorite.
- Locked row: show count ("Locked · 12 notes").
- Empty search vs empty library states already exist — keep.

**Locked folder**
- Naming/clarity pass: the locked row, the folder, and per-note lock semantics read consistently; respect the auto-lock timeout (P4.8).

**Theming & accessibility**
- Dark paper contrast audit (WCAG AA on the dark-brown paper).
- Font-scaling pass (`sp` everywhere, large-font layouts) and RTL margin check — already tracked as pending in `UI-IMPROVEMENTS.md`.
- Haptics on pin/lock/select actions; TalkBack labels for the Σ readouts.

## 5. Sequencing

| Release | Theme | Items | Why now |
|---|---|---|---|
| **v0.2** | Notes-first polish | **Ruled-line drift fix**, card preview + conditional Σ chip, copy total, total-refresh fix, undo snackbar, update-check cadence, auto-lock timeout | All cheap, all reinforce "great notes, bonus math"; makes the app demonstrably better in 60 seconds |
| **v0.3** | Organize & learn | Tags UI + filters, first-run sample, cheat sheet, syntax highlighting v1, keyboard accessory, search improvements | Converts single-purpose tool into an organizer; uses already-paid-for schema |
| **v0.4** | Trust & structure | Encryption-at-rest, auto-backup, labeled subtotals, share-in capture, Navigator migration | The two trust features answer the #1 objection to storing important notes in an offline app |
| **Later** | Reach | Widget, Material You, i18n, iOS; spreadsheet/OCR only on evidence of demand | Bigger bets after the core loop is proven |

## 6. Explicit non-goals (for now)

- **Drifting into a finance/expense tracker** — no aggregates, dashboards, or number-required UX; the calculator serves the note, not the other way around.
- **Accounts / cloud sync** — contradicts the positioning; auto-backup + encrypted export covers the need locally.
- **Analytics SDKs** — privacy brand; use a Settings "Send feedback" link instead.
- **Spreadsheet grid / OCR receipts** — heavy, error-prone, and they compete with the plain-text soul of the product; only revisit with direct user demand.
