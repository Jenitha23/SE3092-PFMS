package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.pfms.app.data.local.entity.SavingsGoalEntity
import com.pfms.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local savings goal persistence, observation, and offline sync tracking.
 * All queries strictly enforce user scoping to prevent cross-account data leaks.
 */
@Dao
interface SavingsGoalDao {

    /**
     * Observes the active, non-deleted savings goal for a user.
     */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND status = 'ACTIVE' AND isDeleted = 0 LIMIT 1")
    fun observeActiveGoal(userId: String): Flow<SavingsGoalEntity?>

    /**
     * Synchronously/suspend fetches the active, non-deleted savings goal for a user.
     */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND status = 'ACTIVE' AND isDeleted = 0 LIMIT 1")
    suspend fun getActiveGoal(userId: String): SavingsGoalEntity?

    /**
     * Fetches a single active (non-deleted) goal record scoped to a specific user.
     */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND id = :goalId AND isDeleted = 0 LIMIT 1")
    suspend fun getGoalById(userId: String, goalId: String): SavingsGoalEntity?

    /**
     * Inserts a new savings goal record.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(entity: SavingsGoalEntity)

    /**
     * Updates an existing savings goal record.
     */
    @Update
    suspend fun updateGoal(entity: SavingsGoalEntity)

    /**
     * Inserts or updates a single savings goal entity.
     */
    @Upsert
    suspend fun upsertGoal(entity: SavingsGoalEntity)

    /**
     * Inserts or updates multiple savings goal entities in a single batch.
     */
    @Upsert
    suspend fun upsertAll(entities: List<SavingsGoalEntity>)

    /**
     * Soft-deletes a goal by marking isDeleted = 1 and setting syncStatus to PENDING_DELETE.
     */
    @Query("UPDATE savings_goals SET isDeleted = 1, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE userId = :userId AND id = :goalId")
    suspend fun markGoalDeleted(
        userId: String,
        goalId: String,
        syncStatus: SyncStatus = SyncStatus.PENDING_DELETE,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Permanently deletes a single savings goal record.
     */
    @Query("DELETE FROM savings_goals WHERE userId = :userId AND id = :goalId")
    suspend fun hardDeleteGoal(userId: String, goalId: String)

    /**
     * Retrieves all goals for a user with pending local mutations.
     */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId AND syncStatus != :syncedStatus")
    suspend fun getPendingGoals(
        userId: String,
        syncedStatus: SyncStatus = SyncStatus.SYNCED
    ): List<SavingsGoalEntity>

    /**
     * Marks a savings goal record as synced.
     */
    @Query("UPDATE savings_goals SET syncStatus = :syncStatus WHERE userId = :userId AND id = :goalId")
    suspend fun markGoalSynced(
        userId: String,
        goalId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    )

    /**
     * Clears all cached savings goal records for a given user (e.g. on logout).
     */
    @Query("DELETE FROM savings_goals WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
