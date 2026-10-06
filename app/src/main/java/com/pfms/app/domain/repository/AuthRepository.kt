package com.pfms.app.domain.repository

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.ReauthCredential
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    /** Emits the signed-in user (or null) immediately and on every change. Survives app restarts. */
    val currentUser: Flow<AuthUser?>

    suspend fun register(displayName: String, email: String, password: String): Result<AuthUser>

    suspend fun login(email: String, password: String): Result<AuthUser>

    /** Always succeeds for well-formed emails, so it cannot be used to discover registered users. */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>

    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    suspend fun updateDisplayName(displayName: String): Result<Unit>

    /** [paymentMethod] must be one of [com.pfms.app.domain.model.PaymentMethods.all] or null. */
    suspend fun updateDefaultPaymentMethod(paymentMethod: String?): Result<Unit>

    suspend fun updatePassword(newPassword: String): Result<Unit>

    /** Call [reauthenticate] first. Fails fast, before deleting any data, if the login is not recent. */
    suspend fun deleteAccount(): Result<Unit>

    suspend fun reauthenticate(credential: ReauthCredential): Result<Unit>

    suspend fun logout(): Result<Unit>
}
