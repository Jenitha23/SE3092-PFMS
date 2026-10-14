package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local income caching, querying, and offline sync tracking.
 * All queries explicitly enforce user scoping to prevent cross-account data leaks.
 */
@Dao
interface IncomeDao {

    /**
     * Observes all active (non-deleted) income records for a user, sorted newest first.
     */
    @Query("SELECT * FROM incomes WHERE userId = :userId AND isDeleted = 0 ORDER BY receivedDate DESC, createdAt DESC")
    fun observeIncome(userId: String): Flow<List<IncomeEntity>>

    /**
     * Fetches a single income record by its ID.
     */
    @Query("SELECT * FROM incomes WHERE id = :incomeId LIMIT 1")
    suspend fun getIncomeById(incomeId: String): IncomeEntity?

    /**
     * Fetches a single income record scoped to a specific user.
     */
    @Query("SELECT * FROM incomes WHERE userId = :userId AND id = :incomeId LIMIT 1")
    suspend fun getIncomeById(userId: String, incomeId: String): IncomeEntity?

    /**
     * Inserts or updates a single income entity.
     */
    @Upsert
    suspend fun upsertIncome(entity: IncomeEntity)

    /**
     * Inserts or updates multiple income entities in a single transaction.
     */
    @Upsert
    suspend fun upsertAll(entities: List<IncomeEntity>)

    /**
     * Soft-deletes an income record by marking isDeleted = 1 and setting syncStatus to PENDING_DELETE.
     */
    @Query("UPDATE incomes SET isDeleted = 1, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE userId = :userId AND id = :incomeId")
    suspend fun softDeleteIncome(
        userId: String,
        incomeId: String,
        syncStatus: SyncStatus = SyncStatus.PENDING_DELETE,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Updates the sync status of a specific income record.
     */
    @Query("UPDATE incomes SET syncStatus = :syncStatus WHERE userId = :userId AND id = :incomeId")
    suspend fun updateSyncStatus(userId: String, incomeId: String, syncStatus: SyncStatus)

    /**
     * Updates the sync status of a specific income record by ID.
     */
    @Query("UPDATE incomes SET syncStatus = :syncStatus WHERE id = :incomeId")
    suspend fun updateSyncStatus(incomeId: String, syncStatus: SyncStatus)

    /**
     * Retrieves all records for a user that have pending local mutations (insert, update, delete).
     */
    @Query("SELECT * FROM incomes WHERE userId = :userId AND syncStatus != :syncedStatus")
    suspend fun getPendingSync(userId: String, syncedStatus: SyncStatus = SyncStatus.SYNCED): List<IncomeEntity>

    /**
     * Retrieves records matching a specific sync status.
     */
    @Query("SELECT * FROM incomes WHERE userId = :userId AND syncStatus = :syncStatus")
    suspend fun getIncomeBySyncStatus(userId: String, syncStatus: SyncStatus): List<IncomeEntity>

    /**
     * Permanently deletes a single income record (used after remote deletion reconciliation).
     */
    @Query("DELETE FROM incomes WHERE userId = :userId AND id = :incomeId")
    suspend fun hardDeleteIncome(userId: String, incomeId: String)

    /**
     * Permanently deletes all soft-deleted records that have been confirmed synced remotely.
     */
    @Query("DELETE FROM incomes WHERE userId = :userId AND isDeleted = 1 AND syncStatus = :syncedStatus")
    suspend fun deleteSyncedDeleted(userId: String, syncedStatus: SyncStatus = SyncStatus.SYNCED)

    /**
     * Clears all cached income records for a given user (e.g. on user logout).
     */
    @Query("DELETE FROM incomes WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
