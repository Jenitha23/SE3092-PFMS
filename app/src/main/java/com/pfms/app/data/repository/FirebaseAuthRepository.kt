package com.pfms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.model.DefaultFinancialData
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
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

            // 1. Create Firebase Authentication account
            val authResult = firebaseAuth
                .createUserWithEmailAndPassword(email, password)
                .await()

            val firebaseUser = authResult.user
                ?: return Result.failure(
                    IllegalStateException("User account could not be created.")
                )

            // 2. Save display name in Firebase Authentication
            firebaseUser.updateProfile(
                userProfileChangeRequest {
                    this.displayName = displayName
                }
            ).await()

            // 3. Reference the user's Firestore document
            val userDocument = firestore
                .collection("users")
                .document(firebaseUser.uid)

            // 4. Create a Firestore batch
            val batch = firestore.batch()

            // 5. Create user profile
            val profileData = hashMapOf<String, Any?>(
                "uid" to firebaseUser.uid,
                "displayName" to displayName,
                "email" to (firebaseUser.email ?: email),
                "baseCurrency" to "LKR",
                "defaultPaymentMethod" to null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            batch.set(userDocument, profileData)

            // 6. Create default expense categories
            DefaultFinancialData.expenseCategories.forEach { categoryName ->

                val categoryDocument = userDocument
                    .collection("categories")
                    .document()

                val categoryData = hashMapOf(
                    "name" to categoryName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                batch.set(categoryDocument, categoryData)
            }

            // 7. Create default income sources
            DefaultFinancialData.incomeSources.forEach { sourceName ->

                val sourceDocument = userDocument
                    .collection("incomeSources")
                    .document()

                val sourceData = hashMapOf(
                    "name" to sourceName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                batch.set(sourceDocument, sourceData)
            }

            // 8. Commit profile + categories + income sources together
            batch.commit().await()

            // 9. Return application authentication model
            val authUser = AuthUser(
                uid = firebaseUser.uid,
                displayName = displayName,
                email = firebaseUser.email ?: email,
                isEmailVerified = firebaseUser.isEmailVerified
            )

            Result.success(authUser)

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

    private fun FirebaseUser.toAuthUser(): AuthUser {

        return AuthUser(
            uid = uid,
            displayName = displayName,
            email = email,
            isEmailVerified = isEmailVerified
        )
    }
}