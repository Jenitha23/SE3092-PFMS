package com.pfms.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pfms.app.data.model.DefaultFinancialData
import com.pfms.app.domain.model.AuthException
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.EmailAuthProvider

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
                    AuthException("Invalid email or password.")
                )

            Result.success(firebaseUser.toAuthUser())

        } catch (exception: Exception) {

            Result.failure(
                AuthException("Invalid email or password.")
            )
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

    override suspend fun signInWithGoogle(
        idToken: String
    ): Result<AuthUser> {

        return try {

            // 1. Create Firebase credential from Google ID token
            val credential = GoogleAuthProvider
                .getCredential(idToken, null)

            // 2. Sign in to Firebase Authentication
            val result = firebaseAuth
                .signInWithCredential(credential)
                .await()

            val firebaseUser = result.user
                ?: return Result.failure(
                    AuthException("Google authentication failed.")
                )

            // 3. Reference the user's Firestore profile
            val userDocument = firestore
                .collection("users")
                .document(firebaseUser.uid)

            // 4. Check whether the user's profile already exists
            val userSnapshot = userDocument
                .get()
                .await()

            // 5. Initialize PFMS data only for a new Google user
            if (!userSnapshot.exists()) {

                val batch = firestore.batch()

                val profileData = hashMapOf<String, Any?>(
                    "uid" to firebaseUser.uid,
                    "displayName" to (firebaseUser.displayName ?: ""),
                    "email" to (firebaseUser.email ?: ""),
                    "baseCurrency" to "LKR",
                    "defaultPaymentMethod" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                batch.set(userDocument, profileData)

                // Default expense categories
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

                // Default income sources
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

                // Save all new-user data together
                batch.commit().await()
            }

            // 6. Return application authentication model
            Result.success(firebaseUser.toAuthUser())

        } catch (exception: Exception) {

            Result.failure(
                AuthException("Google authentication failed.")
            )
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
    override suspend fun updateDisplayName(
        displayName: String
    ): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(
                    AuthException("User is not authenticated.")
                )

            firebaseUser.updateProfile(
                userProfileChangeRequest {
                    this.displayName = displayName
                }
            ).await()

            firestore.collection("users")
                .document(firebaseUser.uid)
                .update(
                    mapOf(
                        "displayName" to displayName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun updateDefaultPaymentMethod(
        paymentMethod: String?
    ): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(
                    AuthException("User is not authenticated.")
                )

            firestore.collection("users")
                .document(firebaseUser.uid)
                .update(
                    mapOf(
                        "defaultPaymentMethod" to paymentMethod,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun updatePassword(
        newPassword: String
    ): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(
                    AuthException("User is not authenticated.")
                )

            firebaseUser.updatePassword(newPassword).await()

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(
                    AuthException("User is not authenticated.")
                )

            val userDocument = firestore
                .collection("users")
                .document(firebaseUser.uid)

            val subcollections = listOf(
                "categories",
                "incomeSources",
                "income",
                "expenses",
                "savingsGoals",
                "recurringTemplates",
                "reminders"
            )

            for (collectionName in subcollections) {
                val snapshot = userDocument
                    .collection(collectionName)
                    .get()
                    .await()

                if (!snapshot.isEmpty) {
                    val batch = firestore.batch()

                    snapshot.documents.forEach { document ->
                        batch.delete(document.reference)
                    }

                    batch.commit().await()
                }
            }

            // Delete the user's main Firestore profile
            userDocument.delete().await()

            // Finally delete the Firebase Authentication account
            firebaseUser.delete().await()

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
    override suspend fun reauthenticate(
        password: String
    ): Result<Unit> {
        return try {
            val firebaseUser = firebaseAuth.currentUser
                ?: return Result.failure(
                    AuthException("User is not authenticated.")
                )

            val email = firebaseUser.email
                ?: return Result.failure(
                    AuthException("This account cannot be re-authenticated with a password.")
                )

            val credential = EmailAuthProvider.getCredential(
                email,
                password
            )

            firebaseUser.reauthenticate(credential).await()

            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(
                AuthException("Re-authentication failed. Please check your password.")
            )
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