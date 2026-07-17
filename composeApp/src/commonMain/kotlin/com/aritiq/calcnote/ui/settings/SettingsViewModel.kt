package com.aritiq.calcnote.ui.settings

import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.export.ImportMode
import com.aritiq.calcnote.data.export.ImportService
import com.aritiq.calcnote.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings holder: theme mode (System / Light / Dark). Persisted offline in the local
 * settings table.
 */
class SettingsViewModel(
    private val repo: SettingsRepository,
    private val exportService: ExportService,
    private val importService: ImportService,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            val all = repo.all()
            _state.value = UiState(
                themeMode = ThemeMode.fromString(all["theme"] ?: "system"),
            )
        }
    }

    fun setTheme(mode: ThemeMode) {
        _state.value = _state.value.copy(themeMode = mode)
        scope.launch { repo.set("theme", mode.persisted) }
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
        val importResult: String? = null,
    )
}