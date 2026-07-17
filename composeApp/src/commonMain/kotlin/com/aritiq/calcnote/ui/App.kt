package com.aritiq.calcnote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.editor.EditorScreen
import com.aritiq.calcnote.ui.folders.ManageFoldersScreen
import com.aritiq.calcnote.ui.home.HomeScreen
import com.aritiq.calcnote.ui.home.LockedFolderScreen
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import com.aritiq.calcnote.ui.settings.SettingsScreen
import com.aritiq.calcnote.ui.settings.SettingsViewModel
import com.aritiq.calcnote.ui.theme.CalcNoteTheme
import com.aritiq.calcnote.ui.theme.SystemBarAppearance
import org.koin.compose.koinInject

@Composable
fun App(navigator: Navigator) {
    val settingsVm = koinInject<SettingsViewModel>()
    LaunchedEffect(Unit) { settingsVm.load() }
    val themeMode = settingsVm.state.collectAsState().value.themeMode

    val darkTheme = when (themeMode) {
        SettingsViewModel.ThemeMode.System -> isSystemInDarkTheme()
        SettingsViewModel.ThemeMode.Light -> false
        SettingsViewModel.ThemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }

    CalcNoteTheme(darkTheme = darkTheme) {
        SystemBarAppearance(darkTheme)
        when (val route = navigator.current.collectAsState().value) {
            Route.Home -> HomeScreen(navigator)
            is Route.Editor -> EditorScreen(navigator, route.noteId)
            Route.Settings -> SettingsScreen(navigator)
            Route.ManageFolders -> ManageFoldersScreen(navigator)
            Route.About -> HomeScreen(navigator)
            Route.LockedFolder -> {
                val lockManager = koinInject<LockManager>()
                LockedFolderScreen(navigator, lockManager)
            }
        }
    }
}
