## Summary

Implements encrypted export/import for Aritiq notes with user-configurable passwords.
Replaces the hardcoded default password with a proper key derivation flow using PBKDF2
and AES-256-GCM. Backward-compatible with previously exported files. Import falls back
to a password prompt when neither the stored key nor the default password match the
file.

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
- Set/change/remove all persist to the existing key-value settings table. Key writes
  are synchronous (`runBlocking`) so export can never read a half-committed key and
  silently fall back to the default password.

### Auto-Use Stored Key

- **Export** (Home, Editor, Settings): reads `export_key` + `export_key_salt` from
  settings, uses raw-key encryption that embeds the derivation salt in the file header.
  Falls back to default password if no key is set.
- **Import**: auto-detects encrypted vs plain JSON. If encrypted, tries the stored key
  first, then the default password. If both fail, shows a **password prompt** dialog —
  the entered password must match the one used to encrypt the file (via PBKDF2 against
  the salt embedded in the file). Wrong password shows an inline error.

### Bug Fixes

- **BAD_DECRYPT on import**: fixed a race where `setExportPassword` wrote the derived
  key asynchronously, so an immediate export read a null key and encrypted with the
  default password, while import later used the stored key — key mismatch → GCM auth
  failure. Key writes are now synchronous.
- **Import of non-default files**: previously import only ever tried the stored key or
  default password; files encrypted under a different password could never be imported.
  Now the password prompt handles that case.

### Encryption Format

- AES-256-GCM with random 12-byte IV per message
- PBKDF2-HMAC-SHA256 with 310,000 iterations for key derivation
- Magic bytes: `ARITIQ_ENC_V1\0` header
- File header: magic + salt (32B) + IV (12B) + ciphertext+tag
- File extension: `.aritiq`

## Files Changed

| File | Change |
|------|--------|
| `EncryptionService.kt` (common expect) | Added `encryptWithKey`, `encryptWithKeyAndSalt`, `decryptWithKey`, `hashPassword`, `verifyPassword`, `deriveKey`, `generateSalt`, `decodeHex` |
| `EncryptionService.android.kt` | Removed hardcoded `PASSWORD`, implemented all new JCA functions; `encryptWithKey` delegates to `encryptWithKeyAndSalt` |
| `ExportBridge.kt` | Changed to `ByteArray` input (shared) |
| `ExportBridge.android.kt` | Changed to write `ByteArray` to cache |
| `SettingsViewModel.kt` | Added `passwordSet` state, sync key writes, `setExportPassword`, `changeExportPassword`, `clearExportPassword`, `getExportKey`, `getExportSalt` |
| `SettingsScreen.kt` | Password section UI, import password-prompt dialog, auto-use stored key + salt for export/import |
| `HomeScreen.kt` | Auto-use stored key + salt for export |
| `EditorScreen.kt` | Injected `settingsRepo`, auto-use stored key + salt for export |
