package com.pfms.app.data.remote.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.pfms.app.data.firebase.FirestoreCollections
import com.pfms.app.data.remote.firestore.mapper.toFirestoreMap
import com.pfms.app.data.remote.firestore.mapper.toGoalTransaction
import com.pfms.app.data.remote.firestore.mapper.toSavingsGoal
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore implementation of [SavingsGoalRemoteDataSource].
 * Enforces strict user isolation and session verification before accessing any user subcollections.
 */
@Singleton
class FirestoreSavingsGoalRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : SavingsGoalRemoteDataSource {

    private fun validateAccess(userId: String, modelUserId: String? = null): Result<Unit> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID must not be blank"))
        }
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Authentication required: no active user session"))
        }
        if (currentUid != userId) {
            return Result.failure(
                SecurityException("Access denied: authenticated user '$currentUid' cannot access resources for user '$userId'")
            )
        }
        if (modelUserId != null && modelUserId != userId) {
            return Result.failure(
                IllegalArgumentException("Cross-user write prevented: path userId '$userId' does not match model userId '$modelUserId'")
            )
        }
        return Result.success(Unit)
    }

    private fun userGoalsCollection(userId: String) =
        firestore.collection(FirestoreCollections.USERS)
            .document(userId)
            .collection(FirestoreCollections.SAVINGS_GOALS)

    private fun userTransactionsCollection(userId: String) =
        firestore.collection(FirestoreCollections.USERS)
            .document(userId)
            .collection(FirestoreCollections.GOAL_CONTRIBUTIONS)

    override suspend fun upsertGoal(
        userId: String,
        goal: SavingsGoal
    ): Result<Unit> {
        validateAccess(userId, goal.userId).onFailure { return Result.failure(it) }
        if (goal.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Goal ID must not be blank"))
        }

        return runCatching {
            userGoalsCollection(userId)
                .document(goal.id)
                .set(goal.toFirestoreMap(), SetOptions.merge())
                .await()
        }
    }

    override suspend fun deleteGoal(
        userId: String,
        goalId: String
    ): Result<Unit> {
        validateAccess(userId).onFailure { return Result.failure(it) }
        if (goalId.isBlank()) {
            return Result.failure(IllegalArgumentException("Goal ID must not be blank"))
        }

        return runCatching {
            userGoalsCollection(userId)
                .document(goalId)
                .delete()
                .await()
        }
    }

    override suspend fun fetchGoals(
        userId: String
    ): Result<List<SavingsGoal>> {
        validateAccess(userId).onFailure { return Result.failure(it) }

        return runCatching {
            val snapshot = userGoalsCollection(userId).get().await()
            snapshot.documents
                .mapNotNull { it.toSavingsGoal() }
                .sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun fetchGoalById(
        userId: String,
        goalId: String
    ): Result<SavingsGoal?> {
        validateAccess(userId).onFailure { return Result.failure(it) }
        if (goalId.isBlank()) {
            return Result.failure(IllegalArgumentException("Goal ID must not be blank"))
        }

        return runCatching {
            val snapshot = userGoalsCollection(userId).document(goalId).get().await()
            if (!snapshot.exists()) {
                null
            } else {
                snapshot.toSavingsGoal()
            }
        }
    }

    override suspend fun upsertTransaction(
        userId: String,
        transaction: GoalTransaction
    ): Result<Unit> {
        validateAccess(userId, transaction.userId).onFailure { return Result.failure(it) }
        if (transaction.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Transaction ID must not be blank"))
        }

        return runCatching {
            userTransactionsCollection(userId)
                .document(transaction.id)
                .set(transaction.toFirestoreMap(), SetOptions.merge())
                .await()
        }
    }

    override suspend fun deleteTransaction(
        userId: String,
        transactionId: String
    ): Result<Unit> {
        validateAccess(userId).onFailure { return Result.failure(it) }
        if (transactionId.isBlank()) {
            return Result.failure(IllegalArgumentException("Transaction ID must not be blank"))
        }

        return runCatching {
            userTransactionsCollection(userId)
                .document(transactionId)
                .delete()
                .await()
        }
    }

    override suspend fun fetchTransactionsForGoal(
        userId: String,
        goalId: String
    ): Result<List<GoalTransaction>> {
        validateAccess(userId).onFailure { return Result.failure(it) }
        if (goalId.isBlank()) {
            return Result.failure(IllegalArgumentException("Goal ID must not be blank"))
        }

        return runCatching {
            // Query by goalId only; sort deterministically in-memory to avoid requiring a composite index
            val snapshot = userTransactionsCollection(userId)
                .whereEqualTo("goalId", goalId)
                .get()
                .await()

            snapshot.documents
                .mapNotNull { it.toGoalTransaction() }
                .sortedWith(compareBy<GoalTransaction> { it.date }.thenBy { it.createdAt })
        }
    }
}
