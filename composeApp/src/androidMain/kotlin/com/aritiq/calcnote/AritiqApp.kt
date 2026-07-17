package com.aritiq.calcnote

import android.app.Application
import com.aritiq.calcnote.data.repository.FolderRepository
import com.aritiq.calcnote.di.appModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

class Aritiq : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(appModule(this@Aritiq))
        }
        scope.launch {
            get<FolderRepository>(FolderRepository::class.java).ensureLockedFolderExists()
        }
    }
}
