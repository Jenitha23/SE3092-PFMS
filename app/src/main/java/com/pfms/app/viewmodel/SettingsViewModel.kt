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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
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
    private val reloadProfile = MutableStateFlow(0)
    private var failedAction: (() -> Unit)? = null
    fun retry() { if (!_uiState.value.isLoading && _uiState.value.canRetry) failedAction?.invoke() }
    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: StateFlow<UserProfile?> = reloadProfile.flatMapLatest { userRepository.observeProfile().catch { error ->
        val problem = com.pfms.app.data.error.OperationErrors.present(error, com.pfms.app.data.error.ErrorAction.LOAD)
        _uiState.value = SettingsUiState(errorMessage = problem.message, canRetry = problem.canRetry)
        failedAction = { reloadProfile.value++ }
    } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val biometricEnabled: StateFlow<Boolean> = biometricPreferences.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** null until read from disk; the one-time prompt is shown only when this is false. */
    val biometricPromptShown: StateFlow<Boolean?> = biometricPreferences.biometricPromptShown
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissBiometricPrompt() {
        viewModelScope.launch { biometricPreferences.setBiometricPromptShown(true) }
    }

    fun enableBiometricFromPrompt() {
        setBiometricEnabled(true)
        dismissBiometricPrompt()
    }

    /** Show the biometric switch as enabled only when this is true. */
    fun isBiometricAvailable(): Boolean = biometricAuthManager.isBiometricAvailable()

    private val _biometricAvailable = MutableStateFlow(isBiometricAvailable())
    val biometricAvailable = _biometricAvailable.asStateFlow()
    fun refreshBiometricAvailability() { _biometricAvailable.value = isBiometricAvailable() }

    /** Real versionName for the "PFMS Version %1$s" label. */
    val appVersion: String = appInfoProvider.versionName

    /** Options for the default payment method picker. */
    val paymentMethods: List<String> = PaymentMethods.all

    val profileState = combine(user, profile, SettingsPresentation::profile)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsProfileState())
    private val _editor = MutableStateFlow(SettingsEditorState())
    val editor = _editor.asStateFlow()

    fun openDialog(dialog: SettingsDialog) {
        if (_uiState.value.isLoading) return
        clearMessages()
        val name = profile.value?.displayName ?: user.value?.displayName.orEmpty()
        _editor.value = SettingsEditorState(dialog = dialog, name = name, originalName = name)
    }

    fun dismissDialog() {
        if (_uiState.value.isLoading) return
        _editor.value = SettingsEditorState()
        clearMessages()
    }

    fun edit(field: SettingsField, value: String) {
        if (_uiState.value.isLoading) return
        clearMessages()
        val old = _editor.value.copy(error = null)
        _editor.value = when (field) {
            SettingsField.NAME -> old.copy(name = value)
            SettingsField.CURRENT_PASSWORD -> old.copy(currentPassword = value)
            SettingsField.NEW_PASSWORD -> old.copy(newPassword = value)
            SettingsField.CONFIRM_PASSWORD -> old.copy(confirmPassword = value)
            SettingsField.DELETE_PASSWORD -> old.copy(deletePassword = value)
        }
    }

    fun saveName() {
        val value = _editor.value
        if (!value.canSaveName) return
        AuthValidator.validateDisplayName(value.name.trim())?.let {
            _editor.value = value.copy(error = it); return
        }
        updateDisplayName(value.name.trim())
    }

    fun savePassword() {
        val value = _editor.value
        val error = when {
            value.confirmPassword.isBlank() -> "Please confirm your new password."
            value.newPassword != value.confirmPassword -> "New password and confirmation do not match."
            else -> null
        }
        if (error != null) { _editor.value = value.copy(error = error); return }
        changePassword(value.currentPassword, value.newPassword)
    }

    fun deleteFromEditor(activityContext: Context) {
        if (profileState.value.googleOnly) deleteAccountWithGoogle(activityContext)
        else if (_editor.value.deletePassword.isNotBlank()) deleteAccountWithPassword(_editor.value.deletePassword)
    }

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
        failedAction = null
        _uiState.value = _uiState.value.copy(message = null, errorMessage = null, canRetry = false)
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
                onSuccess = {
                    _editor.value = SettingsEditorState()
                    _uiState.value = SettingsUiState(accountDeleted = true)
                },
                onFailure = { error ->
                    val problem = com.pfms.app.data.error.OperationErrors.present(error)
                    _uiState.value = SettingsUiState(errorMessage = problem.message, canRetry = problem.canRetry)
                    failedAction = { deleteAccount(credentialProvider) }
                }
            )
        }
    }

    private fun <T> performAction(success: String, action: suspend () -> Result<T>) {
        if (_uiState.value.isLoading) return
        _uiState.value = SettingsUiState(isLoading = true)

        viewModelScope.launch {
            val result = try { withTimeout(20_000) { action() } } catch (error: Exception) {
                com.pfms.app.data.error.OperationErrors.present(error)
                Result.failure(error)
            }
            result.fold(
                onSuccess = {
                    _editor.value = SettingsEditorState()
                    _uiState.value = SettingsUiState(message = success)
                },
                onFailure = { error ->
                    val problem = com.pfms.app.data.error.OperationErrors.present(error)
                    _uiState.value = SettingsUiState(errorMessage = problem.message, canRetry = problem.canRetry)
                    failedAction = { performAction(success, action) }
                }
            )
        }
    }

    private fun fail(message: String) {
        _uiState.value = SettingsUiState(errorMessage = message)
    }

    private fun messageOf(error: Throwable) =
        com.pfms.app.data.error.OperationErrors.present(error).message
}
