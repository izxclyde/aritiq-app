package com.aritiq.calcnote

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aritiq.calcnote.lock.LockManager
import com.aritiq.calcnote.ui.App
import com.aritiq.calcnote.ui.navigation.Navigator
import com.aritiq.calcnote.ui.navigation.Route
import org.koin.java.KoinJavaComponent.get

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val lockManager = get<LockManager>(LockManager::class.java)
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) lockManager.lock()
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
}
