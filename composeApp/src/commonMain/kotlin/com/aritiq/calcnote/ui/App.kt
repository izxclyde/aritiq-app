package com.aritiq.calcnote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.editor.EditorScreen
import com.aritiq.calcnote.ui.folders.ManageFoldersScreen
import com.aritiq.calcnote.ui.home.HomeScreen
import com.aritiq.calcnote.ui.home.LockedFolderScreen
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import com.aritiq.calcnote.ui.settings.SettingsScreen
import com.aritiq.calcnote.ui.settings.SettingsViewModel
import com.aritiq.calcnote.ui.theme.AritiqTheme
import com.aritiq.calcnote.ui.theme.NotebookAccent
import com.aritiq.calcnote.ui.theme.PaperGrainOverlay
import com.aritiq.calcnote.ui.theme.SystemBarAppearance
import org.koin.compose.koinInject

@Composable
fun App(navigator: Navigator) {
    val settingsVm = koinInject<SettingsViewModel>()
    LaunchedEffect(Unit) { settingsVm.load() }
    val settings = settingsVm.state.collectAsState().value
    val themeMode = settings.themeMode
    val accent = settings.accent

    val darkTheme = when (themeMode) {
        SettingsViewModel.ThemeMode.System -> isSystemInDarkTheme()
        SettingsViewModel.ThemeMode.Light -> false
        SettingsViewModel.ThemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }

    AritiqTheme(darkTheme = darkTheme, accent = accent) {
        SystemBarAppearance(darkTheme)
        Box(modifier = Modifier.fillMaxSize()) {
            when (val route = navigator.current.collectAsState().value) {
                Route.Home -> HomeScreen(navigator)
                is Route.Editor -> EditorScreen(navigator, route.noteId)
                Route.Settings -> SettingsScreen(navigator)
                Route.ManageFolders -> ManageFoldersScreen(navigator)
                Route.LockedFolder -> {
                    val lockManager = koinInject<LockManager>()
                    LockedFolderScreen(navigator, lockManager)
                }
            }
            PaperGrainOverlay()
        }
    }
}
