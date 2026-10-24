package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.Transaction
import com.pfms.app.data.local.entity.GoalTransactionEntity
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.domain.usecase.savings.ValidateGoalLedgerUseCase
import com.pfms.app.domain.usecase.savings.ValidateMonthlyContributionUseCase
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.ExpenseEntity
import com.pfms.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local savings goal transaction ledger persistence, querying, and sync tracking.
 * All queries strictly enforce user scoping and goal scoping.
 */
@Dao
interface GoalTransactionDao {
    /** Goal existence and opening balance are read in the same Room transaction as the ledger write. */
    @Query("SELECT openingSavedAmountMinor FROM savings_goals WHERE userId = :userId AND id = :goalId AND isDeleted = 0 LIMIT 1")
    suspend fun openingBalance(userId: String, goalId: String): Long?

    // Read all relevant financial records in the same Room transaction as the goal write.
    // This includes pending offline records but excludes deleted and unresolved expense conflicts.
    @Query("SELECT * FROM incomes WHERE userId = :userId AND isDeleted = 0")
    suspend fun contributionIncomeRows(userId: String): List<IncomeEntity>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND isDeleted = 0 AND needsConfirmation = 0")
    suspend fun contributionExpenseRows(userId: String): List<ExpenseEntity>

    @Query("SELECT * FROM goal_transactions WHERE userId = :userId AND isDeleted = 0")
    suspend fun allActiveGoalTransactionRows(userId: String): List<GoalTransactionEntity>

    private suspend fun validateContributionLimit(candidate: com.pfms.app.domain.model.GoalTransaction,
                                                  @Suppress("UNUSED_PARAMETER") existing: List<com.pfms.app.domain.model.GoalTransaction>) {
        val incomes = contributionIncomeRows(candidate.userId).map { it.toDomain() }
        val expenses = contributionExpenseRows(candidate.userId).map { it.toDomain() }
        val allOtherTransactions = allActiveGoalTransactionRows(candidate.userId)
            .toDomain().filterNot { it.id == candidate.id }
        ValidateMonthlyContributionUseCase.requireContributionAllowed(candidate, incomes, expenses, allOtherTransactions)
    }

    @Transaction
    suspend fun insertTransactionGuarded(entity: GoalTransactionEntity) {
        val opening = openingBalance(entity.userId, entity.goalId)
            ?: throw NoSuchElementException("Savings goal not found")
        require(getTransactionById(entity.userId, entity.id) == null) { "Transaction ID already exists" }
        val ledger = getTransactionsForGoal(entity.userId, entity.goalId).toDomain()
        ValidateGoalLedgerUseCase.validate(opening, ledger + entity.toDomain())
        validateContributionLimit(entity.toDomain(), ledger)
        insertTransaction(entity)
    }

    @Transaction
    suspend fun updateTransactionGuarded(entity: GoalTransactionEntity) {
        val existing = getTransactionById(entity.userId, entity.id)
            ?: throw NoSuchElementException("Transaction not found")
        require(existing.goalId == entity.goalId) { "Cannot change transaction goal" }
        val opening = openingBalance(entity.userId, entity.goalId)
            ?: throw NoSuchElementException("Savings goal not found")
        val ledger = getTransactionsForGoal(entity.userId, entity.goalId).toDomain()
            .filterNot { it.id == entity.id }
        ValidateGoalLedgerUseCase.validate(opening, ledger + entity.toDomain())
        validateContributionLimit(entity.toDomain(), ledger)
        upsertTransaction(entity)
    }

    @Transaction
    suspend fun deleteTransactionGuarded(userId: String, transactionId: String, deletedAt: Long) {
        val existing = getTransactionById(userId, transactionId)
            ?: throw NoSuchElementException("Transaction not found")
        val opening = openingBalance(userId, existing.goalId)
            ?: throw NoSuchElementException("Savings goal not found")
        val ledger = getTransactionsForGoal(userId, existing.goalId).toDomain()
            .filterNot { it.id == transactionId }
        ValidateGoalLedgerUseCase.validate(opening, ledger)
        markTransactionDeleted(userId, transactionId, SyncStatus.PENDING_DELETE, deletedAt)
    }

    @Transaction
    suspend fun completeSync(submitted: GoalTransactionEntity) {
        if (getPendingTransactions(submitted.userId).none { it == submitted }) return
        if (submitted.isDeleted) hardDeleteTransaction(submitted.userId, submitted.id)
        else markTransactionSynced(submitted.userId, submitted.id)
    }


    /**
     * Observes all active (non-deleted) transactions for a specific goal, ordered by date ASC, createdAt ASC.
     */
    @Query("SELECT * FROM goal_transactions WHERE userId = :userId AND goalId = :goalId AND isDeleted = 0 ORDER BY date ASC, createdAt ASC")
    fun observeTransactionsForGoal(userId: String, goalId: String): Flow<List<GoalTransactionEntity>>

    /**
     * Fetches all active (non-deleted) transactions for a specific goal, ordered by date ASC, createdAt ASC.
     */
    @Query("SELECT * FROM goal_transactions WHERE userId = :userId AND goalId = :goalId AND isDeleted = 0 ORDER BY date ASC, createdAt ASC")
    suspend fun getTransactionsForGoal(userId: String, goalId: String): List<GoalTransactionEntity>

    /**
     * Fetches a single active (non-deleted) transaction by ID scoped to a user.
     */
    @Query("SELECT * FROM goal_transactions WHERE userId = :userId AND id = :transactionId AND isDeleted = 0 LIMIT 1")
    suspend fun getTransactionById(userId: String, transactionId: String): GoalTransactionEntity?

    /**
     * Inserts a single goal transaction record.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(entity: GoalTransactionEntity)

    /**
     * Updates an existing goal transaction record.
     */
    @Update
    suspend fun updateTransaction(entity: GoalTransactionEntity)

    /**
     * Inserts or updates a single goal transaction record.
     */
    @Upsert
    suspend fun upsertTransaction(entity: GoalTransactionEntity)

    /**
     * Inserts or updates multiple goal transactions in a single batch.
     */
    @Upsert
    suspend fun upsertAll(entities: List<GoalTransactionEntity>)

    /**
     * Soft-deletes a transaction by marking isDeleted = 1 and setting syncStatus to PENDING_DELETE.
     */
    @Query("UPDATE goal_transactions SET isDeleted = 1, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE userId = :userId AND id = :transactionId")
    suspend fun markTransactionDeleted(
        userId: String,
        transactionId: String,
        syncStatus: SyncStatus = SyncStatus.PENDING_DELETE,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Permanently deletes a single transaction record.
     */
    @Query("DELETE FROM goal_transactions WHERE userId = :userId AND id = :transactionId")
    suspend fun hardDeleteTransaction(userId: String, transactionId: String)

    /**
     * Retrieves all goal transactions for a user that have pending local mutations.
     */
    @Query("SELECT * FROM goal_transactions WHERE userId = :userId AND syncStatus != :syncedStatus")
    suspend fun getPendingTransactions(
        userId: String,
        syncedStatus: SyncStatus = SyncStatus.SYNCED
    ): List<GoalTransactionEntity>

    /**
     * Marks a transaction record as synced.
     */
    @Query("UPDATE goal_transactions SET syncStatus = :syncStatus WHERE userId = :userId AND id = :transactionId")
    suspend fun markTransactionSynced(
        userId: String,
        transactionId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    )

    /**
     * Clears all cached goal transactions for a given user (e.g. on logout).
     */
    @Query("DELETE FROM goal_transactions WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
