package com.aritiq.calcnote

actual fun appVersion(): String = BuildConfig.VERSION_NAME

actual fun isDebugBuild(): Boolean = BuildConfig.DEBUG
