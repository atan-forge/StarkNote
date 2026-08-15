package com.stark.note.ui.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.stark.note.R

class BiometricGate(
    private val activity: FragmentActivity
) {
    fun authenticate(
        onSuccess: () -> Unit,
        onPinFallback: () -> Unit,
        onCancel: () -> Unit
    ) {
        if (!canAuthenticate()) {
            onPinFallback()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onPinFallback()
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onCancel()
                        else -> onPinFallback()
                    }
                }
            }
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_title))
                .setSubtitle(activity.getString(R.string.biometric_subtitle))
                .setNegativeButtonText(activity.getString(R.string.use_pin))
                .build()
        )
    }

    fun canAuthenticate(): Boolean {
        return BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
}

internal object BiometricUnlockPolicy {
    fun shouldUse(hasPin: Boolean, available: Boolean, enabled: Boolean): Boolean {
        return hasPin && available && enabled
    }
}
