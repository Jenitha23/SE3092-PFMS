package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Counts the existing durable per-row queues; no second operation store is created. */
@Dao
interface PendingChangesDao {
    @Query("""SELECT
        (SELECT count(*) FROM incomes WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM expenses WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM salary_templates WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM savings_goals WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM goal_transactions WHERE userId=:userId AND syncStatus!='SYNCED')""")
    fun observePendingCount(userId: String): Flow<Int>

    @Query("""SELECT
        (SELECT count(*) FROM incomes WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM expenses WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM salary_templates WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM savings_goals WHERE userId=:userId AND syncStatus!='SYNCED') +
        (SELECT count(*) FROM goal_transactions WHERE userId=:userId AND syncStatus!='SYNCED')""")
    suspend fun pendingCount(userId: String): Int
}
