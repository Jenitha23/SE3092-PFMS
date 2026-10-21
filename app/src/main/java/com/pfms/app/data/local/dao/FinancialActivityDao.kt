package com.pfms.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialActivityDao {
    // Deleted rows can still prove past logging activity. No transaction-date or updatedAt fallback.
    @Query("""SELECT MAX(recordedAt) FROM (
        SELECT createdAt AS recordedAt FROM incomes WHERE userId=:userId AND createdAt>0
        UNION ALL
        SELECT createdAt AS recordedAt FROM expenses WHERE userId=:userId AND createdAt>0
    )""")
    fun observeLastRecordedAt(userId: String): Flow<Long?>
}
