package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local salary template caching and offline sync tracking.
 * Ensures strict user isolation so one user's recurring template is never visible to another.
 */
@Dao
interface SalaryTemplateDao {
    @Transaction
    suspend fun completeSync(submitted: SalaryTemplateEntity) {
        if (getPendingSync(submitted.userId).none { it == submitted }) return
        if (submitted.isDeleted) deleteTemplateById(submitted.userId, submitted.id)
        else upsertSalaryTemplate(submitted.copy(syncStatus = SyncStatus.SYNCED))
    }

    @Query("DELETE FROM salary_templates WHERE userId=:userId AND id=:templateId")
    suspend fun deleteTemplateById(userId: String, templateId: String)

    @Query("DELETE FROM salary_templates WHERE userId=:userId AND syncStatus='SYNCED' AND id NOT IN (:ids)")
    suspend fun removeMissingSynced(userId: String, ids: List<String>)

    @Transaction
    suspend fun reconcile(userId: String, rows: List<SalaryTemplateEntity>, authoritative: Boolean) {
        val local = getSalaryTemplate(userId)
        if (local == null || local.syncStatus == SyncStatus.SYNCED) {
            rows.maxByOrNull { it.updatedAt }?.takeIf { local == null || it.updatedAt >= local.updatedAt }
                ?.let { upsertSalaryTemplate(it) }
        }
        if (authoritative) removeMissingSynced(userId, rows.map { it.id })
    }

    /**
     * Observes the active recurring salary template for a user.
     */
    @Query("SELECT * FROM salary_templates WHERE userId = :userId AND isDeleted = 0 LIMIT 1")
    fun observeSalaryTemplate(userId: String): Flow<SalaryTemplateEntity?>

    /**
     * Fetches the active recurring salary template for a user synchronously/suspend.
     */
    @Query("SELECT * FROM salary_templates WHERE userId = :userId AND isDeleted = 0 LIMIT 1")
    suspend fun getSalaryTemplate(userId: String): SalaryTemplateEntity?

    /**
     * Inserts or updates a recurring salary template.
     */
    @Upsert
    suspend fun upsertSalaryTemplate(entity: SalaryTemplateEntity)

    /**
     * Updates the sync status for a user's salary template.
     */
    @Query("UPDATE salary_templates SET syncStatus = :syncStatus WHERE userId = :userId")
    suspend fun updateSyncStatus(userId: String, syncStatus: SyncStatus)

    /**
     * Retrieves any salary template mutations pending synchronization for a user.
     */
    @Query("SELECT * FROM salary_templates WHERE userId = :userId AND syncStatus != :syncedStatus")
    suspend fun getPendingSync(userId: String, syncedStatus: SyncStatus = SyncStatus.SYNCED): List<SalaryTemplateEntity>

    /**
     * Soft-deletes a salary template for a user.
     */
    @Query("UPDATE salary_templates SET isDeleted = 1, syncStatus = :syncStatus, updatedAt = :updatedAt WHERE userId = :userId")
    suspend fun softDeleteSalaryTemplate(
        userId: String,
        syncStatus: SyncStatus = SyncStatus.PENDING_DELETE,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * Permanently deletes a salary template for a user.
     */
    @Query("DELETE FROM salary_templates WHERE userId = :userId")
    suspend fun hardDeleteSalaryTemplate(userId: String)

    /**
     * Clears cached salary template for a given user (e.g. on logout).
     */
    @Query("DELETE FROM salary_templates WHERE userId = :userId")
    suspend fun clearAllForUser(userId: String)
}
