package com.pfms.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.validation.AuthFieldErrors
import com.pfms.app.domain.validation.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FR-01, FR-02, FR-03: register, login, password reset and Google sign-in. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInManager: GoogleSignInManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun register(displayName: String, email: String, password: String) {
        val errors = AuthValidator.validateRegistration(displayName.trim(), email.trim(), password)
        if (rejectInvalid(errors)) return

        runAuthAction { authRepository.register(displayName, email, password) }
    }

    fun login(email: String, password: String) {
        val errors = AuthValidator.validateLogin(email.trim(), password)
        if (rejectInvalid(errors)) return

        runAuthAction { authRepository.login(email, password) }
    }

    fun sendPasswordReset(email: String) {
        val errors = AuthFieldErrors(email = AuthValidator.validateEmail(email.trim()))
        if (rejectInvalid(errors)) return

        runAuthAction(
            successMessage = "If an account exists for that email, a password reset link has been sent."
        ) { authRepository.sendPasswordResetEmail(email) }
    }

    /**
     * @param activityContext an Activity context (Credential Manager needs one to show its sheet).
     * It is used only for the duration of the call and is never stored.
     */
    fun signInWithGoogle(activityContext: Context) {
        runAuthAction {
            googleSignInManager.getGoogleIdToken(activityContext).fold(
                onSuccess = { idToken -> authRepository.signInWithGoogle(idToken) },
                onFailure = { error -> Result.failure<AuthUser>(error) }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null, fieldErrors = AuthFieldErrors()) }
    }

    private fun rejectInvalid(errors: AuthFieldErrors): Boolean {
        if (!errors.hasErrors) return false
        _uiState.update { AuthUiState(fieldErrors = errors) }
        return true
    }

    private fun <T> runAuthAction(
        successMessage: String? = null,
        action: suspend () -> Result<T>
    ) {
        if (_uiState.value.isLoading) return // ignore double taps while a request is running

        _uiState.value = AuthUiState(isLoading = true)

        viewModelScope.launch {
            action().fold(
                onSuccess = { _uiState.value = AuthUiState(infoMessage = successMessage) },
                onFailure = { error ->
                    _uiState.value = AuthUiState(
                        errorMessage = error.message ?: "Something went wrong. Please try again."
                    )
                }
            )
        }
    }
}
