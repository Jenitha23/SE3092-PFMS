package com.pfms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.toAuthUser())
        }

        firebaseAuth.addAuthStateListener(listener)

        awaitClose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    override suspend fun register(
        displayName: String,
        email: String,
        password: String
    ): Result<AuthUser> {
        return try {
            val result = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            val firebaseUser = result.user
                ?: return Result.failure(
                    IllegalStateException("User account could not be created.")
                )

            val profile = AuthUser(
                uid = firebaseUser.uid,
                displayName = displayName,
                email = firebaseUser.email,
                isEmailVerified = firebaseUser.isEmailVerified
            )

            Result.success(profile)

        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun login(
        email: String,
        password: String
    ): Result<AuthUser> {
        return try {
            val result = firebaseAuth
                .signInWithEmailAndPassword(email, password)
                .await()

            val firebaseUser = result.user
                ?: return Result.failure(
                    IllegalStateException("User could not be authenticated.")
                )

            Result.success(firebaseUser.toAuthUser())

        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun sendPasswordResetEmail(
        email: String
    ): Result<Unit> {
        return try {
            firebaseAuth
                .sendPasswordResetEmail(email)
                .await()

            Result.success(Unit)

        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            Result.success(Unit)

        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private fun com.google.firebase.auth.FirebaseUser.toAuthUser(): AuthUser {
        return AuthUser(
            uid = uid,
            displayName = displayName,
            email = email,
            isEmailVerified = isEmailVerified
        )
    }
}