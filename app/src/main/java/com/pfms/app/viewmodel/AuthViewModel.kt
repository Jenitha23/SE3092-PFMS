package com.pfms.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.RememberedLoginRepository
import com.pfms.app.domain.validation.PasswordStrength
import com.pfms.app.domain.validation.AuthFieldErrors
import com.pfms.app.domain.validation.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** FR-01, FR-02, FR-03: register, login, password reset and Google sign-in. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInManager: GoogleSignInManager,
    private val rememberedLoginRepository: RememberedLoginRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var formEdited = false

    fun onAction(action: AuthAction) {
        if (_uiState.value.isLoading) return
        when (action) {
            is AuthAction.EmailChanged -> onEmailChanged(action.value)
            is AuthAction.NameChanged -> onDisplayNameChanged(action.value)
            is AuthAction.PasswordChanged -> onPasswordChanged(action.value)
            is AuthAction.RememberMeChanged -> onRememberMeChanged(action.value)
            is AuthAction.TermsAcceptedChanged -> onTermsAcceptedChanged(action.value)
            AuthAction.TogglePasswordVisibility -> togglePasswordVisibility()
            AuthAction.Login -> submitLogin()
            AuthAction.Register -> submitRegistration()
            AuthAction.ShowTerms -> showTermsNotice()
            AuthAction.DismissTerms -> dismissTermsNotice()
        }
    }

    init {
        viewModelScope.launch {
            val email = rememberedLoginRepository.getEmail()
            if (!formEdited && email != null) {
                _uiState.update { it.copy(email = email, rememberMe = true) }
            }
        }
    }

    fun onEmailChanged(value: String) {
        formEdited = true
        clearMessages()
        _uiState.update { it.copy(email = value) }
    }

    fun onDisplayNameChanged(value: String) {
        clearMessages()
        _uiState.update { it.copy(displayName = value) }
    }

    fun onPasswordChanged(value: String) {
        clearMessages()
        _uiState.update { it.copy(password = value, passwordStrength = PasswordStrength.evaluate(value)) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun onRememberMeChanged(value: Boolean) {
        formEdited = true
        _uiState.update { it.copy(rememberMe = value) }
        if (!value) viewModelScope.launch { saveRememberedEmail(null) }
    }

    fun onTermsAcceptedChanged(value: Boolean) {
        _uiState.update { it.copy(termsAccepted = value, termsError = null) }
    }

    fun showTermsNotice() { _uiState.update { it.copy(showTermsNotice = true) } }
    fun dismissTermsNotice() { _uiState.update { it.copy(showTermsNotice = false) } }

    fun submitLogin() {
        val state = _uiState.value
        login(state.email, state.password)
    }

    fun submitRegistration() {
        val state = _uiState.value
        val errors = AuthValidator.validateRegistration(state.displayName, state.email, state.password)
        if (rejectInvalid(errors) || !checkTermsAccepted()) return
        register(state.displayName, state.email, state.password)
    }

    fun submitGoogleRegistration(context: Context) {
        if (checkTermsAccepted()) signUpWithGoogle(context)
    }

    private fun checkTermsAccepted(): Boolean {
        if (_uiState.value.termsAccepted) return true
        _uiState.update { it.copy(termsError = "Please agree before creating an account.") }
        return false
    }

    private suspend fun saveRememberedEmail(email: String?) {
        try {
            rememberedLoginRepository.setEmail(email)
        } catch (_: java.io.IOException) {
            // An optional local preference must not prevent authentication.
        }
    }

    fun register(displayName: String, email: String, password: String) {
        val errors = AuthValidator.validateRegistration(displayName.trim(), email.trim(), password)
        if (rejectInvalid(errors)) return

        runAuthAction { authRepository.register(displayName.trim(), email.trim(), password) }
    }

    fun login(email: String, password: String) {
        val errors = AuthValidator.validateLogin(email.trim(), password)
        if (rejectInvalid(errors)) return

        val rememberMe = _uiState.value.rememberMe
        runAuthAction {
            authRepository.login(email.trim(), password).onSuccess {
                // Firebase can navigate away and clear this ViewModel as soon as login succeeds.
                withContext(NonCancellable) {
                    saveRememberedEmail(if (rememberMe) email.trim() else null)
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val errors = AuthFieldErrors(email = AuthValidator.validateEmail(email.trim()))
        if (rejectInvalid(errors)) return

        runAuthAction(
            successMessage = "If an account exists for that email, a password reset link has been sent."
        ) { authRepository.sendPasswordResetEmail(email) }
    }

    /** "Continue with Google" on the Log In screen: only succeeds for an already-registered account. */
    fun signInWithGoogle(activityContext: Context) {
        runAuthAction {
            googleSignInManager.getGoogleIdToken(activityContext).fold(
                onSuccess = { idToken -> authRepository.signInWithGoogle(idToken) },
                onFailure = { error -> Result.failure<AuthUser>(error) }
            )
        }
    }

    /** "Sign up with Google" on the Sign Up screen: fails with "already registered" for existing accounts. */
    fun signUpWithGoogle(activityContext: Context) {
        runAuthAction {
            googleSignInManager.getGoogleIdToken(activityContext).fold(
                onSuccess = { idToken -> authRepository.signUpWithGoogle(idToken) },
                onFailure = { error -> Result.failure<AuthUser>(error) }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null, fieldErrors = AuthFieldErrors()) }
    }

    private fun rejectInvalid(errors: AuthFieldErrors): Boolean {
        if (!errors.hasErrors) return false
        _uiState.update { it.copy(fieldErrors = errors, errorMessage = null, infoMessage = null) }
        return true
    }

    private fun <T> runAuthAction(
        successMessage: String? = null,
        action: suspend () -> Result<T>
    ) {
        if (_uiState.value.isLoading) return // ignore double taps while a request is running

        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null, fieldErrors = AuthFieldErrors()) }

        viewModelScope.launch {
            action().fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, infoMessage = successMessage, password = "",
                        passwordVisible = false, passwordStrength = PasswordStrength.EMPTY) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        errorMessage = if (error is com.pfms.app.domain.model.AuthException) error.message else "Something went wrong. Please try again."
                    ) }
                }
            )
        }
    }
}
