package com.aritiq.calcnote

import android.app.Application
import com.aritiq.calcnote.di.appModule
import org.koin.core.context.startKoin

class Aritiq : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            modules(appModule(this@Aritiq))
        }
    }
}