package com.pfms.app.domain.model

/** Machine-readable reason for an [AuthException], so the UI can react without parsing text. */
enum class AuthErrorType {
    VALIDATION,
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    NETWORK,
    TOO_MANY_REQUESTS,
    REQUIRES_RECENT_LOGIN,
    NOT_AUTHENTICATED,
    PROFILE_SETUP_FAILED,
    UNKNOWN
}
