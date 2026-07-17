package com.aritiq.calcnote.lock

import kotlinx.coroutines.flow.StateFlow

interface LockManager {
    val isUnlocked: StateFlow<Boolean>
    suspend fun authenticate(activity: Any): Boolean
    fun isAvailable(): Boolean
    fun lock()
}
