package com.pfms.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.AppInfoProvider
import com.pfms.app.data.auth.BiometricAuthManager
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.PaymentMethods
import com.pfms.app.domain.model.ReauthCredential
import com.pfms.app.domain.model.UserProfile
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.UserRepository
import com.pfms.app.domain.usecase.DeleteAccountUseCase
import com.pfms.app.domain.validation.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    /** Becomes true after a successful deletion. The session flow will also flip to Unauthenticated. */
    val accountDeleted: Boolean = false
)

/** FR-06 (and the biometric switch from FR-04). */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    userRepository: UserRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val googleSignInManager: GoogleSignInManager,
    private val biometricAuthManager: BiometricAuthManager,
    private val biometricPreferences: BiometricPreferences,
    appInfoProvider: AppInfoProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val user: StateFlow<AuthUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Real-time profile (display name, default payment method). */
    val profile: StateFlow<UserProfile?> = userRepository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val biometricEnabled: StateFlow<Boolean> = biometricPreferences.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Show the biometric switch as enabled only when this is true. */
    fun isBiometricAvailable(): Boolean = biometricAuthManager.isBiometricAvailable()

    /** Real versionName for the "PFMS Version %1$s" label. */
    val appVersion: String = appInfoProvider.versionName

    /** Options for the default payment method picker. */
    val paymentMethods: List<String> = PaymentMethods.all

    fun updateDisplayName(name: String) =
        performAction(success = "Name updated.") { authRepository.updateDisplayName(name) }

    fun updateDefaultPaymentMethod(paymentMethod: String?) =
        performAction(success = "Default payment method updated.") {
            authRepository.updateDefaultPaymentMethod(paymentMethod)
        }

    fun changePassword(currentPassword: String, newPassword: String) {
        if (user.value?.canChangePassword == false) {
            fail("This account signs in with Google and has no password to change.")
            return
        }
        AuthValidator.validateNewPassword(newPassword)?.let { fail(it); return }
        if (newPassword == currentPassword) {
            fail("Your new password must be different from the current one.")
            return
        }

        performAction(success = "Password changed.") {
            authRepository.reauthenticate(ReauthCredential.Password(currentPassword))
                .fold(
                    onSuccess = { authRepository.updatePassword(newPassword) },
                    onFailure = { Result.failure<Unit>(it) }
                )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !biometricAuthManager.isBiometricAvailable()) {
            fail("No fingerprint or face is enrolled on this device. Add one in system settings first.")
            return
        }
        viewModelScope.launch { biometricPreferences.setBiometricEnabled(enabled) }
    }

    /** For email/password accounts. */
    fun deleteAccountWithPassword(password: String) =
        deleteAccount { Result.success(ReauthCredential.Password(password)) }

    /** For Google accounts. [activityContext] is used only during the call. */
    fun deleteAccountWithGoogle(activityContext: Context) = deleteAccount {
        googleSignInManager.getGoogleIdToken(activityContext).map { ReauthCredential.Google(it) }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null)
    }

    private fun deleteAccount(credentialProvider: suspend () -> Result<ReauthCredential>) {
        if (_uiState.value.isLoading) return
        _uiState.value = SettingsUiState(isLoading = true)

        viewModelScope.launch {
            val result = credentialProvider().fold(
                onSuccess = { credential -> deleteAccountUseCase(credential) },
                onFailure = { error -> Result.failure<Unit>(error) }
            )
            result.fold(
                onSuccess = { _uiState.value = SettingsUiState(accountDeleted = true) },
                onFailure = { error -> _uiState.value = SettingsUiState(errorMessage = messageOf(error)) }
            )
        }
    }

    private fun <T> performAction(success: String, action: suspend () -> Result<T>) {
        if (_uiState.value.isLoading) return
        _uiState.value = SettingsUiState(isLoading = true)

        viewModelScope.launch {
            action().fold(
                onSuccess = { _uiState.value = SettingsUiState(message = success) },
                onFailure = { error -> _uiState.value = SettingsUiState(errorMessage = messageOf(error)) }
            )
        }
    }

    private fun fail(message: String) {
        _uiState.value = SettingsUiState(errorMessage = message)
    }

    private fun messageOf(error: Throwable) =
        error.message ?: "Something went wrong. Please try again."
}
