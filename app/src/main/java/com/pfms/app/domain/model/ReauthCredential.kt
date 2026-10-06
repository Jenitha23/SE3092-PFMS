package com.pfms.app.domain.model

/** Proof of identity for sensitive actions (change password, delete account, unlock fallback). */
sealed interface ReauthCredential {
    data class Password(val value: String) : ReauthCredential
    data class Google(val idToken: String) : ReauthCredential
}
