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
import com.aritiq.calcnote.lock.AndroidLockManager
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.editor.EditorViewModel
import com.aritiq.calcnote.ui.folders.ManageFoldersViewModel
import com.aritiq.calcnote.ui.home.HomeViewModel
import com.aritiq.calcnote.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

fun appModule(context: Context): Module = module {
    single<DriverFactory> { DriverFactory(context) }
    single<SqlDriver> { get<DriverFactory>().createDriver(AritiqDatabase.Schema) }
    single { AritiqDatabase(get()) }
    single<NoteRepository> { SqlDelightNoteRepository(get()) }
    single<FolderRepository> { SqlDelightFolderRepository(get()) }
    single<SettingsRepository> { SqlDelightSettingsRepository(get()) }
    single<LockManager> { AndroidLockManager(context) }
    single { ExportService(get(), get()) }
    single { ImportService(get(), get()) }

    factory { HomeViewModel(get(), get(), get(), get(), get()) }
    factory { EditorViewModel(get(), get(), get()) }
    factory { ManageFoldersViewModel(get()) }
    single { SettingsViewModel(get(), get(), get()) }
}

private fun appModuleQualifier() = qualifier("app")
