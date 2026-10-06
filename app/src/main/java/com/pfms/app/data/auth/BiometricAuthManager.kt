package com.pfms.app.data.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject



class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** True only when the device has supported biometric hardware AND at least one enrolled biometric. */
    fun isBiometricAvailable(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt and reports exactly ONE terminal result.
     * A single unrecognised fingerprint (onAuthenticationFailed) is NOT terminal: the system
     * prompt stays open and lets the user retry, so it is deliberately not reported.
     */
    fun authenticate(
        activity: FragmentActivity,
        onResult: (BiometricResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    onResult(BiometricResult.Success)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED ->
                            onResult(BiometricResult.UseFallback)
                        else ->
                            onResult(BiometricResult.Error(errString.toString()))
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PFMS")
            .setSubtitle("Use your fingerprint or face to unlock the application")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setNegativeButtonText("Use standard authentication")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private companion object {
        // WEAK includes STRONG, so face unlock on devices that classify it as Class 2 also works.
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}