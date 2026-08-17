package com.aritiq.calcnote.ui.settings

import com.aritiq.calcnote.data.export.EncryptionService
import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.export.ImportMode
import com.aritiq.calcnote.data.export.ImportService
import com.aritiq.calcnote.data.export.decodeHex
import com.aritiq.calcnote.data.repository.SettingsRepository
import com.aritiq.calcnote.ui.theme.NotebookAccent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val exportService: ExportService,
    private val importService: ImportService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val encryptionService = EncryptionService()

    fun load() {
        scope.launch {
            val all = repo.all()
            _state.value = UiState(
                themeMode = ThemeMode.fromString(all["theme"] ?: "system"),
                accent = NotebookAccent.fromString(all["accent"] ?: ""),
                passwordSet = all.containsKey("export_password_hash"),
            )
        }
    }

    fun setTheme(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        scope.launch { repo.set("theme", mode.persisted) }
    }

    fun setAccent(accent: NotebookAccent) {
        _state.value = _state.value.copy(accent = accent)
        scope.launch { repo.set("accent", accent.name) }
    }

    fun setExportPassword(password: String) {
        val hash = encryptionService.hashPassword(password)
        val salt = encryptionService.generateSalt()
        val key = encryptionService.deriveKey(password, salt)
        runBlocking {
            repo.set("export_password_hash", hash)
            repo.set("export_key_salt", salt.joinToString("") { "%02x".format(it) })
            repo.set("export_key", key.joinToString("") { "%02x".format(it) })
        }
        _state.value = _state.value.copy(passwordSet = true)
    }

    fun changeExportPassword(oldPassword: String, newPassword: String): Boolean {
        val storedHash = runBlocking { repo.get("export_password_hash") } ?: return false
        if (!encryptionService.verifyPassword(oldPassword, storedHash)) return false
        setExportPassword(newPassword)
        return true
    }

    fun clearExportPassword() {
        scope.launch {
            repo.set("export_password_hash", "")
            repo.set("export_key_salt", "")
            repo.set("export_key", "")
        }
        _state.value = _state.value.copy(passwordSet = false)
    }

    suspend fun getExportKey(): ByteArray? {
        val keyHex = repo.get("export_key") ?: return null
        if (keyHex.isBlank()) return null
        return decodeHex(keyHex)
    }

    suspend fun getExportSalt(): ByteArray? {
        val saltHex = repo.get("export_key_salt") ?: return null
        if (saltHex.isBlank()) return null
        return decodeHex(saltHex)
    }

    suspend fun verifyExportPassword(password: String): Boolean {
        val hash = repo.get("export_password_hash") ?: return false
        if (hash.isBlank()) return false
        return encryptionService.verifyPassword(password, hash)
    }

    enum class ThemeMode(val persisted: String) {
        System("system"), Light("light"), Dark("dark");
        companion object {
            fun fromString(value: String): ThemeMode = entries.firstOrNull { it.persisted == value } ?: System
        }
    }

    fun importResult(msg: String) {
        _state.value = _state.value.copy(importResult = msg)
    }

    suspend fun exportAllJson(): String = exportService.exportAllJson()

    suspend fun importFromString(content: String, mode: ImportMode): String {
        val result = importService.import(content, mode)
        val msg = "Imported: ${result.imported}, skipped: ${result.skipped}" +
            if (result.errors.isNotEmpty()) "\nErrors: ${result.errors.joinToString(", ")}" else ""
        _state.value = _state.value.copy(importResult = msg)
        return msg
    }

    data class UiState(
        val themeMode: ThemeMode = ThemeMode.System,
        val accent: NotebookAccent = NotebookAccent.TEAL,
        val importResult: String? = null,
        val passwordSet: Boolean = false,
    )
}

private fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}
