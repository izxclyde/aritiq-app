## Summary

Implements encrypted export/import for Aritiq notes with user-configurable passwords.
Replaces the hardcoded default password with a proper key derivation flow using PBKDF2
and AES-256-GCM. Backward-compatible with previously exported files.

---

## What's New

### User-Configurable Export Password (Settings)

- **Password section** in Settings screen between Accent and Export: set, change, or
  remove an export password.
- Password is never stored in plaintext. Instead:
  1. Password → SHA-256 hash (stored in `export_password_hash`)
  2. Password + random 32-byte salt → PBKDF2-HMAC-SHA256 (310k iterations) → 32-byte
     AES key (stored in `export_key`)
  3. Salt stored as hex in `export_key_salt`
- Set/change/remove all persist to the existing key-value settings table.

### Auto-Use Stored Key

- **Export** (Home, Editor, Settings): reads `export_key` from settings, uses raw-key
  encryption. Falls back to default password if no key is set.
- **Import**: auto-detects encrypted vs plain JSON. If encrypted, tries stored key first;
  if decryption fails, falls back to default password. No user prompts needed in the
  normal path.

### Encryption Format (unchanged from previous PR)

- AES-256-GCM with random 12-byte IV per message
- PBKDF2-HMAC-SHA256 with 310,000 iterations for key derivation
- Magic bytes: `ARITIQ_ENC_V1\0` header
- File extension: `.aritiq`

## Files Changed

| File | Change |
|------|--------|
| `EncryptionService.kt` (common expect) | Added `encryptWithKey`, `decryptWithKey`, `hashPassword`, `verifyPassword`, `deriveKey`, `generateSalt` |
| `EncryptionService.android.kt` | Removed hardcoded `PASSWORD` constant, implemented all new JCA functions |
| `ExportBridge.kt` | Changed to `ByteArray` input (shared) |
| `ExportBridge.android.kt` | Changed to write `ByteArray` to cache |
| `SettingsViewModel.kt` | Added `passwordSet` state, `setExportPassword`, `changeExportPassword`, `clearExportPassword`, `getExportKey`, `verifyExportPassword` |
| `SettingsScreen.kt` | Added password section UI (set/change/remove dialogs), auto-use stored key for export/import |
| `HomeScreen.kt` | Auto-use stored key for export (reads `export_key` from settings) |
| `EditorScreen.kt` | Injected `settingsRepo`, auto-use stored key for export |
