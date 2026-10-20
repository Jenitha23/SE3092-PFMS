package com.pfms.app.data.local.dao

import androidx.room.*
import com.pfms.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE userId=:userId AND isDeleted=0 AND needsConfirmation=0 ORDER BY date DESC, createdAt DESC")
    fun observe(userId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE userId=:userId AND needsConfirmation=1 AND isDeleted=0 ORDER BY createdAt LIMIT 1")
    fun observeConflict(userId: String): Flow<ExpenseEntity?>

    @Query("SELECT * FROM expenses WHERE userId=:userId AND id=:id")
    suspend fun get(userId: String, id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE userId=:userId AND isDeleted=0 AND needsConfirmation=0 AND createdAt>=:since")
    suspend fun recent(userId: String, since: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE userId=:userId AND syncStatus!='SYNCED' AND needsConfirmation=0 ORDER BY createdAt")
    suspend fun pending(userId: String): List<ExpenseEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE userId=:userId AND (syncStatus!='SYNCED' OR needsConfirmation=1))")
    suspend fun hasPendingOrConflicting(userId: String): Boolean

    @Upsert
    suspend fun upsert(row: ExpenseEntity)

    @Query("UPDATE expenses SET syncStatus='SYNCED' WHERE userId=:userId AND id=:id AND revision=:revision")
    suspend fun acknowledge(userId: String, id: String, revision: String)

    @Query("UPDATE expenses SET needsConfirmation=1 WHERE userId=:userId AND id=:id AND revision=:revision")
    suspend fun markConflict(userId: String, id: String, revision: String)

    @Query("DELETE FROM expenses WHERE userId=:userId AND id=:id AND revision=:revision")
    suspend fun deleteRevision(userId: String, id: String, revision: String)

    @Query("DELETE FROM expenses WHERE userId=:userId AND syncStatus='SYNCED' AND id NOT IN (:ids)")
    suspend fun removeMissing(userId: String, ids: List<String>)

    @Transaction
    suspend fun reconcile(userId: String, rows: List<ExpenseEntity>, authoritative: Boolean) {
        rows.forEach { remote ->
            val local = get(userId, remote.id)
            if (local == null || local.syncStatus == "SYNCED") upsert(remote)
        }
        if (authoritative) removeMissing(userId, rows.map { it.id })
    }
}
