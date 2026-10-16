package com.pfms.app.domain.repository

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository contract for managing savings goals and goal ledger transactions.
 * Completely free from Room and Firebase data-layer dependencies.
 */
interface SavingsGoalRepository {

    /**
     * Observes the single active savings goal for the authenticated user as a reactive stream.
     */
    fun observeActiveGoal(): Flow<SavingsGoal?>

    /**
     * Observes all non-deleted transactions for a given goal, ordered chronologically.
     */
    fun observeTransactions(goalId: String): Flow<List<GoalTransaction>>

    /**
     * Fetches a goal by its ID from local storage.
     */
    suspend fun getGoalById(goalId: String): Result<SavingsGoal?>

    /**
     * Creates a new savings goal offline-first.
     * Enforces that only one ACTIVE goal may exist at a time (FR-23).
     */
    suspend fun createGoal(goal: SavingsGoal): Result<Unit>

    /**
     * Updates an existing savings goal.
     * Enforces the one-active-goal rule if activating an archived or completed goal.
     */
    suspend fun updateGoal(goal: SavingsGoal): Result<Unit>

    /**
     * Archives an existing savings goal.
     */
    suspend fun archiveGoal(goalId: String): Result<Unit>

    /**
     * Soft-deletes a savings goal locally and triggers remote deletion.
     */
    suspend fun deleteGoal(goalId: String): Result<Unit>

    /**
     * Appends a new contribution or withdrawal transaction to a goal's ledger (FR-24).
     */
    suspend fun addTransaction(transaction: GoalTransaction): Result<Unit>

    /**
     * Updates an existing ledger transaction.
     */
    suspend fun updateTransaction(transaction: GoalTransaction): Result<Unit>

    /**
     * Soft-deletes a transaction locally and triggers remote deletion.
     */
    suspend fun deleteTransaction(transactionId: String): Result<Unit>

    /**
     * Synchronizes all locally queued pending goals (inserts, updates, deletes) to Firestore.
     * Returns the count of successfully synchronized goal records.
     */
    suspend fun syncPendingGoals(): Result<Int>

    /**
     * Synchronizes all locally queued pending transactions (inserts, updates, deletes) to Firestore.
     * Returns the count of successfully synchronized transaction records.
     */
    suspend fun syncPendingTransactions(): Result<Int>

    /**
     * Reconciles remote savings goals and transactions into the local Room database,
     * giving precedence to local pending mutations.
     */
    suspend fun refreshRemoteSavingsData(): Result<Unit>
}
