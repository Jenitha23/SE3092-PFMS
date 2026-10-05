package com.pfms.app.domain.repository

import com.pfms.app.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val currentUser: Flow<AuthUser?>

    suspend fun register(
        displayName: String,
        email: String,
        password: String
    ): Result<AuthUser>

    suspend fun login(
        email: String,
        password: String
    ): Result<AuthUser>

    suspend fun sendPasswordResetEmail(
        email: String
    ): Result<Unit>

    suspend fun signInWithGoogle(
        idToken: String
    ): Result<AuthUser>

    suspend fun updateDisplayName(displayName: String): Result<Unit>

    suspend fun updateDefaultPaymentMethod(paymentMethod: String?): Result<Unit>
    suspend fun updatePassword(newPassword: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
    suspend fun reauthenticate(
        password: String
    ): Result<Unit>

    suspend fun logout(): Result<Unit>
}