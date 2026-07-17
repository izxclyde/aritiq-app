package com.aritiq.calcnote.ui.settings

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

    data class UiState(
        val themeMode: ThemeMode = ThemeMode.System,
    )
}