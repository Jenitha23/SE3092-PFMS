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

    suspend fun logout(): Result<Unit>
}