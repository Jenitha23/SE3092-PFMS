package com.pfms.app.data

import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.ReauthCredential
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {

    val userFlow = MutableStateFlow<AuthUser?>(initialUser)

    override val currentUser: Flow<AuthUser?> = userFlow.asStateFlow()

    override suspend fun register(displayName: String, email: String, password: String): Result<AuthUser> {
        val user = AuthUser(uid = "user-123", displayName = displayName, email = email, isEmailVerified = true)
        userFlow.value = user
        return Result.success(user)
    }

    override suspend fun login(email: String, password: String): Result<AuthUser> {
        val user = AuthUser(uid = "user-123", displayName = "Test User", email = email, isEmailVerified = true)
        userFlow.value = user
        return Result.success(user)
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        val user = AuthUser(uid = "user-123", displayName = "Google User", email = "google@test.com", isEmailVerified = true)
        userFlow.value = user
        return Result.success(user)
    }

    override suspend fun signUpWithGoogle(idToken: String): Result<AuthUser> {
        val user = AuthUser(uid = "user-123", displayName = "Google User", email = "google@test.com", isEmailVerified = true)
        userFlow.value = user
        return Result.success(user)
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> = Result.success(Unit)

    override suspend fun updateDefaultPaymentMethod(paymentMethod: String?): Result<Unit> = Result.success(Unit)

    override suspend fun updatePassword(newPassword: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAccount(): Result<Unit> {
        userFlow.value = null
        return Result.success(Unit)
    }

    override suspend fun reauthenticate(credential: ReauthCredential): Result<Unit> = Result.success(Unit)

    override suspend fun logout(): Result<Unit> {
        userFlow.value = null
        return Result.success(Unit)
    }
}
