package com.pfms.app.domain.model

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val isEmailVerified: Boolean,
    /** Sign-in providers linked to the account, e.g. "password", "google.com". */
    val providerIds: List<String> = emptyList()
) {
    /** Google-only accounts have no password, so "Change password" must be hidden for them. */
    val canChangePassword: Boolean get() = PASSWORD_PROVIDER in providerIds

    val isGoogleAccount: Boolean get() = GOOGLE_PROVIDER in providerIds

    companion object {
        const val PASSWORD_PROVIDER = "password"
        const val GOOGLE_PROVIDER = "google.com"
    }
}
