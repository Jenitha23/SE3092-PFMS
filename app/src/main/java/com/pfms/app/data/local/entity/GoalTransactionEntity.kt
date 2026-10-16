package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an individual ledger transaction for a savings goal (FR-24).
 * Scoped by both [userId] and [goalId].
 */
@Entity(
    tableName = "goal_transactions",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["goalId"]),
        Index(value = ["userId", "goalId"]),
        Index(value = ["userId", "date"]),
        Index(value = ["userId", "syncStatus"])
    ]
)
data class GoalTransactionEntity(
    @PrimaryKey
    val id: String,
    val goalId: String,
    val userId: String,
    val amountMinor: Long = 0L,
    val transactionType: String = "CONTRIBUTION",
    val date: String = "",
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val isDeleted: Boolean = false
)
