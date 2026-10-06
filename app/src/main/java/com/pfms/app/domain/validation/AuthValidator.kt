package com.pfms.app.domain.validation

data class AuthFieldErrors(
    val displayName: String? = null,
    val email: String? = null,
    val password: String? = null
) {
    val hasErrors: Boolean get() = displayName != null || email != null || password != null
    val first: String? get() = displayName ?: email ?: password
}

/**
 * Pure input validation (no Android or Firebase types) so it is trivially unit-testable.
 * Each function returns an error message, or null when the input is valid.
 */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_DISPLAY_NAME_LENGTH = 50

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun validateDisplayName(displayName: String): String? = when {
        displayName.isBlank() -> "Please enter your name."
        displayName.trim().length > MAX_DISPLAY_NAME_LENGTH ->
            "Name must be $MAX_DISPLAY_NAME_LENGTH characters or fewer."
        else -> null
    }

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Please enter your email address."
        !EMAIL_REGEX.matches(email.trim()) -> "Please enter a valid email address."
        else -> null
    }

    /** New passwords (register / change password): FR-01 requires at least 8 characters. */
    fun validateNewPassword(password: String): String? =
        if (password.length < MIN_PASSWORD_LENGTH) {
            "Password must be at least $MIN_PASSWORD_LENGTH characters."
        } else null

    /** Existing passwords (login): only require presence, older accounts may be shorter. */
    fun validateExistingPassword(password: String): String? =
        if (password.isEmpty()) "Please enter your password." else null

    fun validateRegistration(displayName: String, email: String, password: String) =
        AuthFieldErrors(
            displayName = validateDisplayName(displayName),
            email = validateEmail(email),
            password = validateNewPassword(password)
        )

    fun validateLogin(email: String, password: String) =
        AuthFieldErrors(
            email = validateEmail(email),
            password = validateExistingPassword(password)
        )
}
