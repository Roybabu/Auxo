package com.auxo.pos.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * The phone's own fingerprint check. The app never sees a fingerprint: it only learns that the check passed.
 * That is enough here because it only resumes a person who is already signed in on this running app; it does
 * not release a password or any stored secret.
 */
interface FingerprintCheck {
    /** True when the phone has a strong fingerprint (or similar) sensor with something enrolled. */
    fun isAvailable(): Boolean

    fun authenticate(title: String, cancelLabel: String, onSuccess: () -> Unit)

    /**
     * The same check, but it also releases [cipher] (a key that only a fingerprint can open). [onFailed] is called when a finger
     * is not recognised; cancelling calls neither callback. Nothing happens where no check is offered.
     */
    fun authenticateWithCipher(title: String, cancelLabel: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onFailed: () -> Unit = {}) = Unit
}

/** Null where no check is offered (previews, tests, a screen that is not inside the activity). */
val LocalFingerprint = staticCompositionLocalOf<FingerprintCheck?> { null }

class BiometricFingerprintCheck(private val activity: FragmentActivity) : FingerprintCheck {

    override fun isAvailable(): Boolean =
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun authenticate(title: String, cancelLabel: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancelLabel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info)
    }

    override fun authenticateWithCipher(title: String, cancelLabel: String, cipher: Cipher, onSuccess: (Cipher) -> Unit, onFailed: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    result.cryptoObject?.cipher?.let(onSuccess) ?: onFailed()
                }

                override fun onAuthenticationFailed() = onFailed()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancelLabel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
