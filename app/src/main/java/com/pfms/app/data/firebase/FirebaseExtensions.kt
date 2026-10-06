package com.pfms.app.data.firebase

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.pfms.app.domain.model.AuthErrorType
import com.pfms.app.domain.model.AuthException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal const val WRITE_TIMEOUT_MS = 10_000L
internal const val BULK_WRITE_TIMEOUT_MS = 30_000L

internal const val OFFLINE_MESSAGE =
    "You appear to be offline. Please check your connection and try again."

/**
 * Firestore write Tasks only complete once the SERVER acknowledges the write, so a plain
 * await() hangs forever while offline (the write itself stays queued). For actions that need
 * the server (settings, account deletion) we wait with a timeout and report a clear error.
 *
 * Do NOT use this for offline-first writes (expenses, income): those must return immediately
 * and rely on the local cache + automatic sync (FR-35).
 */
internal suspend fun Task<*>.awaitOnline(timeoutMs: Long = WRITE_TIMEOUT_MS) {
    withTimeoutOrNull(timeoutMs) {
        this@awaitOnline.await()
        true
    } ?: throw AuthException(OFFLINE_MESSAGE, AuthErrorType.NETWORK)
}

/**
 * Maps any Firebase/Firestore failure to a plain-language [AuthException] (FR-02, FR-36).
 * Order matters: FirebaseAuthWeakPasswordException extends FirebaseAuthInvalidCredentialsException.
 */
internal fun Throwable.toAuthException(): AuthException = when (this) {
    is AuthException -> this
    is FirebaseNetworkException -> AuthException(OFFLINE_MESSAGE, AuthErrorType.NETWORK)
    is FirebaseTooManyRequestsException -> AuthException(
        "Too many attempts. Please wait a moment and try again.",
        AuthErrorType.TOO_MANY_REQUESTS
    )
    is FirebaseAuthUserCollisionException -> AuthException(
        "An account with this email already exists.",
        AuthErrorType.EMAIL_IN_USE
    )
    is FirebaseAuthWeakPasswordException -> AuthException(
        "Please choose a stronger password.",
        AuthErrorType.WEAK_PASSWORD
    )
    is FirebaseAuthRecentLoginRequiredException -> AuthException(
        "For your security, please confirm your identity and try again.",
        AuthErrorType.REQUIRES_RECENT_LOGIN
    )
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> AuthException(
        // Deliberately identical for "unknown email" and "wrong password" (FR-02).
        "Invalid email or password.",
        AuthErrorType.INVALID_CREDENTIALS
    )
    is FirebaseFirestoreException ->
        if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
            AuthException(OFFLINE_MESSAGE, AuthErrorType.NETWORK)
        } else {
            AuthException("Something went wrong. Please try again.", AuthErrorType.UNKNOWN)
        }
    else -> AuthException("Something went wrong. Please try again.", AuthErrorType.UNKNOWN)
}
