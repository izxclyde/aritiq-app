package com.aritiq.calcnote.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class AndroidLockManager(private val context: Context) : LockManager {
    private val _isUnlocked = MutableStateFlow(false)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    override fun isAvailable(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    override suspend fun authenticate(activity: Any): Boolean = suspendCancellableCoroutine { cont ->
        val fa = activity as? FragmentActivity
        if (fa == null) {
            if (cont.isActive) cont.resume(false)
            return@suspendCancellableCoroutine
        }
        val executor = Executors.newSingleThreadExecutor()
        val prompt = BiometricPrompt(
            fa,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    _isUnlocked.value = true
                    executor.shutdown()
                    if (cont.isActive) cont.resume(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    executor.shutdown()
                    if (cont.isActive) cont.resume(false)
                }
                override fun onAuthenticationFailed() {
                    executor.shutdown()
                    if (cont.isActive) cont.resume(false)
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Locked Folder")
                .setSubtitle("Authenticate to view locked notes")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
        )
        cont.invokeOnCancellation { executor.shutdown() }
    }

    override fun lock() {
        _isUnlocked.value = false
    }
}
