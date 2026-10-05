package com.pfms.app.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.auth.BiometricAuthManager
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.pfms.app.domain.model.LogoutResult

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInManager: GoogleSignInManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val biometricPreferences: BiometricPreferences
) : ViewModel() {

    // Current Firebase authentication session
    val currentUser: Flow<AuthUser?> =
        authRepository.currentUser

    // Whether the user has enabled biometric unlock
    val biometricEnabled: StateFlow<Boolean> =
        biometricPreferences.biometricEnabled.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    // ---------------------------------------------------------
    // Google Sign-In
    // ---------------------------------------------------------

    fun signInWithGoogle(
        context: Context,
        onResult: (Result<AuthUser>) -> Unit
    ) {
        viewModelScope.launch {

            val tokenResult =
                googleSignInManager.getGoogleIdToken(context)

            if (tokenResult.isFailure) {

                onResult(
                    Result.failure(
                        tokenResult.exceptionOrNull()
                            ?: Exception("Google authentication failed.")
                    )
                )

                return@launch
            }

            val idToken = tokenResult.getOrNull()

            if (idToken == null) {

                onResult(
                    Result.failure(
                        Exception("Google authentication failed.")
                    )
                )

                return@launch
            }

            val result =
                authRepository.signInWithGoogle(idToken)

            onResult(result)
        }
    }

    // ---------------------------------------------------------
    // Biometric Authentication
    // ---------------------------------------------------------

    fun isBiometricAvailable(
        context: Context
    ): Boolean {

        return biometricAuthManager
            .isBiometricAvailable(context)
    }

    fun setBiometricEnabled(
        enabled: Boolean
    ) {

        viewModelScope.launch {

            biometricPreferences
                .setBiometricEnabled(enabled)
        }
    }

    fun authenticateWithBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {

        biometricAuthManager.authenticate(
            activity = activity,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    fun updateDisplayName(
        displayName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.updateDisplayName(displayName)
            onResult(result)
        }
    }

    fun updateDefaultPaymentMethod(
        paymentMethod: String?,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result =
                authRepository.updateDefaultPaymentMethod(paymentMethod)
            onResult(result)
        }
    }

    fun updatePassword(
        newPassword: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.updatePassword(newPassword)
            onResult(result)
        }
    }

    fun deleteAccount(
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.deleteAccount()
            onResult(result)
        }
    }

    fun reauthenticate(
        password: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.reauthenticate(password)
            onResult(result)
        }
    }

    //---------------------------------------------------
    //Logout function
    //---------------------------------------------------
    fun logout(onResult: (LogoutResult) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.logout()

            if (result.isSuccess) {
                onResult(LogoutResult.Success)
            } else {
                onResult(
                    LogoutResult.Failure(
                        result.exceptionOrNull()?.message
                            ?: "Logout failed."
                    )
                )
            }
        }
    }
}