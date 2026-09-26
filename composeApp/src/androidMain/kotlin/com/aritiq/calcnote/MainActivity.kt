package com.aritiq.calcnote

import android.os.Bundle
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aritiq.calcnote.data.repository.SettingsRepository
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.App
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val lockManager = get<LockManager>(LockManager::class.java)
        val settingsRepo = get<SettingsRepository>(SettingsRepository::class.java)
        val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        var stoppedAt = 0L

        // Auto-lock policy: "immediately" (default) locks on ON_STOP; longer timeouts lock on
        // the next ON_START once the backgrounded time exceeds them. The setting is re-read on
        // every event so changes apply without restart.
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stoppedAt = SystemClock.elapsedRealtime()
                    mainScope.launch {
                        if (autoLockTimeoutMs(settingsRepo.get("auto_lock_timeout")) == 0L) {
                            lockManager.lock()
                        }
                    }
                }
                Lifecycle.Event.ON_START -> {
                    mainScope.launch {
                        val timeoutMs = autoLockTimeoutMs(settingsRepo.get("auto_lock_timeout"))
                        if (timeoutMs > 0L && stoppedAt > 0L &&
                            SystemClock.elapsedRealtime() - stoppedAt >= timeoutMs
                        ) {
                            lockManager.lock()
                        }
                    }
                }
                else -> Unit
            }
        })

        setContent {
            val navigator = remember { Navigator() }
            val currentRoute by navigator.current.collectAsState()
            BackHandler(enabled = currentRoute != Route.Home) {
                navigator.pop()
            }
            App(navigator)
        }
    }

    private fun autoLockTimeoutMs(setting: String?): Long = when (setting) {
        "1min" -> 60_000L
        "5min" -> 300_000L
        else -> 0L
    }
}
