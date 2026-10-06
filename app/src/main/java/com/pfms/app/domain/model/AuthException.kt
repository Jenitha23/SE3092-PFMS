package com.pfms.app.domain.model

/**
 * Domain-level error. [message] is always plain language and safe to show to the user
 * (FR-02, FR-36): it never contains Firebase error codes or stack details.
 */
class AuthException(
    override val message: String,
    val type: AuthErrorType = AuthErrorType.UNKNOWN
) : Exception(message)
