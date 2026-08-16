package com.aritiq.calcnote.lock

import android.content.Context
import android.os.Build
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
        return bm.canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun authenticators() =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
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
                    // Biometric read failed to match — keep the prompt open so the user can retry.
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Locked Folder")
            .setSubtitle("Authenticate to view locked notes")
            .setAllowedAuthenticators(authenticators())
        if (Build.VERSION.SDK_INT < 30) {
            builder.setNegativeButtonText("Cancel")
        }
        prompt.authenticate(builder.build())
        cont.invokeOnCancellation { executor.shutdown() }
    }

    override fun lock() {
        _isUnlocked.value = false
    }
}
