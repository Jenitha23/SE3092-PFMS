package com.pfms.app.viewmodel

import com.pfms.app.domain.validation.AuthFieldErrors

/**
 * Everything the login / register / forgot-password screens need.
 * Navigation after success is driven by [SessionViewModel.session], not by this state.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    /** Plain-language error to show in a banner/snackbar; null when there is none. */
    val errorMessage: String? = null,
    /** Neutral confirmation, e.g. "If an account exists, a reset link has been sent". */
    val infoMessage: String? = null,
    /** Inline per-field validation errors (display name, email, password). */
    val fieldErrors: AuthFieldErrors = AuthFieldErrors(),
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val rememberMe: Boolean = false,
    val termsAccepted: Boolean = false,
    val termsError: String? = null,
    val showTermsNotice: Boolean = false,
    val passwordStrength: com.pfms.app.domain.validation.PasswordStrength =
        com.pfms.app.domain.validation.PasswordStrength.EMPTY
)
