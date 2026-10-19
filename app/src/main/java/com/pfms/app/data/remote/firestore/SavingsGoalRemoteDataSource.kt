package com.pfms.app.data.remote.firestore

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal

/**
 * Remote data source contract for Firestore operations on savings goals and goal transactions.
 * Completely decoupled from local Room entities and caching.
 */
interface SavingsGoalRemoteDataSource {

    /**
     * Upserts a savings goal document under users/{userId}/savingsGoals/{goal.id}.
     */
    suspend fun upsertGoal(
        userId: String,
        goal: SavingsGoal
    ): Result<Unit>

    /**
     * Deletes a savings goal document at users/{userId}/savingsGoals/{goalId}.
     */
    suspend fun deleteGoal(
        userId: String,
        goalId: String
    ): Result<Unit>

    /**
     * Fetches all savings goals for a user under users/{userId}/savingsGoals.
     */
    suspend fun fetchGoals(
        userId: String
    ): Result<List<SavingsGoal>>

    /**
     * Fetches a specific savings goal by ID under users/{userId}/savingsGoals/{goalId}.
     * Returns Result.success(null) if the document does not exist.
     */
    suspend fun fetchGoalById(
        userId: String,
        goalId: String
    ): Result<SavingsGoal?>

    /**
     * Upserts a goal transaction document under users/{userId}/goalContributions/{transaction.id}.
     */
    suspend fun upsertTransaction(
        userId: String,
        transaction: GoalTransaction
    ): Result<Unit>

    /**
     * Deletes a goal transaction document at users/{userId}/goalContributions/{transactionId}.
     */
    suspend fun deleteTransaction(
        userId: String,
        transactionId: String
    ): Result<Unit>

    /**
     * Fetches all transactions for a specific goal under users/{userId}/goalContributions.
     * Filtered by goalId and ordered by date ASC, then createdAt ASC.
     */
    suspend fun fetchTransactionsForGoal(
        userId: String,
        goalId: String
    ): Result<List<GoalTransaction>>
}
