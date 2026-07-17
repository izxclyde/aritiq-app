package com.aritiq.calcnote.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.aritiq.calcnote.data.db.AritiqDatabase
import com.aritiq.calcnote.data.db.DriverFactory
import com.aritiq.calcnote.data.export.ExportService
import com.aritiq.calcnote.data.export.ImportService
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.data.repository.NoteRepository
import com.aritiq.calcnote.data.repository.SettingsRepository
import com.aritiq.calcnote.data.repository.SqlDelightFolderRepository
import com.aritiq.calcnote.data.repository.SqlDelightNoteRepository
import com.aritiq.calcnote.data.repository.SqlDelightSettingsRepository
import com.aritiq.calcnote.ui.editor.EditorViewModel
import com.aritiq.calcnote.ui.folders.ManageFoldersViewModel
import com.aritiq.calcnote.ui.home.HomeViewModel
import com.aritiq.calcnote.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/**
 * Wires dependencies for the Android target. SQLDelight's [DriverFactory] lives in
 * [androidMain] and exposes a Context-bound actual. ViewModels are constructed via
 * constructor injection so any future iOS host can register them with the same module
 * (only the driver factory differs).
 */
fun appModule(context: Context): Module = module {
    single<DriverFactory> { DriverFactory(context) }
    single<SqlDriver> { get<DriverFactory>().createDriver(AritiqDatabase.Schema) }
    single { AritiqDatabase(get()) }
    single<NoteRepository> { SqlDelightNoteRepository(get()) }
    single<FolderRepository> { SqlDelightFolderRepository(get()) }
    single<SettingsRepository> { SqlDelightSettingsRepository(get()) }
    single { ExportService(get(), get()) }
    single { ImportService(get(), get()) }

    // ponytail: Home and Editor are factory-scoped (per-screen state). Settings is a
    // single — it holds global state (theme) shared by App.kt and the settings screen.
    factory { HomeViewModel(get(), get(), get(), get()) }
    factory { EditorViewModel(get(), get(), get()) }
    factory { ManageFoldersViewModel(get()) }
    single { SettingsViewModel(get(), get(), get()) }
}

private fun appModuleQualifier() = qualifier("app")