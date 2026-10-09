package com.pfms.app.viewmodel

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.auth.BiometricAuthManager
import com.pfms.app.data.auth.BiometricPreferences
import com.pfms.app.data.auth.GoogleSignInManager
import com.pfms.app.data.auth.BiometricResult
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.model.ReauthCredential
import com.pfms.app.domain.model.SessionState
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the app content may be shown (FR-04). */
enum class AppLockState {
    /** Still reading the session / biometric setting. Show a splash, never the content. */
    Checking,
    Locked,
    Unlocked
}

data class LockUiState(
    val isLoading: Boolean = false,
    /** True once biometrics were dismissed or failed: show the password / Google fallback. */
    val showFallback: Boolean = false,
    val message: String? = null
)

/**
 * Session (FR-02), app lock (FR-04) and logout (FR-05).
 * Scope this to the Activity (hiltViewModel() in the root composable) so it is shared.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val logoutUseCase: LogoutUseCase,
    private val biometricAuthManager: BiometricAuthManager,
    private val biometricPreferences: BiometricPreferences,
    private val googleSignInManager: GoogleSignInManager
) : ViewModel() {

    /** Drives the start destination: Loading -> splash, Unauthenticated -> login, Authenticated -> home. */
    val session: StateFlow<SessionState> = authRepository.currentUser
        .map { user ->
            if (user == null) SessionState.Unauthenticated else SessionState.Authenticated(user)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    /** null until the persisted biometric setting has been read. */
    private val locked = MutableStateFlow<Boolean?>(null)

    val lockState: StateFlow<AppLockState> = combine(session, locked) { sessionState, isLocked ->
        when {
            sessionState is SessionState.Loading || isLocked == null -> AppLockState.Checking
            sessionState is SessionState.Authenticated && isLocked -> AppLockState.Locked
            else -> AppLockState.Unlocked
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockState.Checking)

    private val _lockUiState = MutableStateFlow(LockUiState())
    val lockUiState: StateFlow<LockUiState> = _lockUiState.asStateFlow()

    private val _logoutResult = MutableStateFlow<LogoutResult?>(null)

    /** Success / UnsynchronizedDataWarning / Failure. Call [consumeLogoutResult] once handled. */
    val logoutResult: StateFlow<LogoutResult?> = _logoutResult.asStateFlow()

    init {
        // Cold start: lock immediately if the user enabled biometrics.
        viewModelScope.launch {
            locked.value = biometricPreferences.biometricEnabled.first()
        }

        // A fresh sign-in (Unauthenticated -> Authenticated) must not ask for a fingerprint.
        viewModelScope.launch {
            var previous: SessionState = SessionState.Loading
            session.collect { current ->
                if (previous is SessionState.Unauthenticated && current is SessionState.Authenticated) {
                    locked.value = false
                }
                previous = current
            }
        }
    }

    // ---------------------------------------------------------------- FR-04 app lock

    /** Call when the app returns from the background after your chosen timeout. */
    fun lockApp() {
        viewModelScope.launch { locked.value = biometricPreferences.biometricEnabled.first() }
    }

    fun unlockWithBiometric(activity: FragmentActivity) {
        biometricAuthManager.authenticate(activity) { result ->
            when (result) {
                BiometricResult.Success -> unlock()
                BiometricResult.UseFallback ->
                    _lockUiState.update { it.copy(showFallback = true, message = null) }
                is BiometricResult.Error ->
                    _lockUiState.update { it.copy(showFallback = true, message = result.message) }
            }
        }
    }

    /** Standard-authentication fallback for email/password accounts. */
    fun unlockWithPassword(password: String) =
        unlockWithCredential { Result.success(ReauthCredential.Password(password)) }

    /** Standard-authentication fallback for Google accounts. */
    fun unlockWithGoogle(activityContext: Context) = unlockWithCredential {
        googleSignInManager.getGoogleIdToken(activityContext).map { ReauthCredential.Google(it) }
    }

    private fun unlockWithCredential(credentialProvider: suspend () -> Result<ReauthCredential>) {
        if (_lockUiState.value.isLoading) return
        _lockUiState.update { it.copy(isLoading = true, message = null) }

        viewModelScope.launch {
            val outcome = credentialProvider().fold(
                onSuccess = { credential -> authRepository.reauthenticate(credential) },
                onFailure = { error -> Result.failure<Unit>(error) }
            )
            outcome.fold(
                onSuccess = { unlock() },
                onFailure = { error ->
                    _lockUiState.update {
                        it.copy(isLoading = false, message = error.message ?: "Could not unlock. Please try again.")
                    }
                }
            )
        }
    }

    private fun unlock() {
        locked.value = false
        _lockUiState.value = LockUiState()
    }

    // ---------------------------------------------------------------- FR-05 logout

    /**
     * First call (force = false) returns UnsynchronizedDataWarning when entries are still waiting
     * to sync. The UI shows a confirmation dialog and, if the user insists, calls logout(force = true).
     */
    fun logout(force: Boolean = false) {
        viewModelScope.launch {
            val result = logoutUseCase(force)
            _logoutResult.value = result
            if (result is LogoutResult.Success) {
                // Firebase signOut alone does not clear Credential Manager's active Google
                // credential state. Clear it so the next explicit Google sign-in starts fresh.
                googleSignInManager.clearCredentialState()
            }
        }
    }

    fun consumeLogoutResult() {
        _logoutResult.value = null
    }
}
