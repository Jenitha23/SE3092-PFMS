package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a savings goal stored locally for offline caching and synchronization (FR-23, FR-28).
 * Scoped by [userId] to ensure cross-user isolation.
 */
@Entity(
    tableName = "savings_goals",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "status"]),
        Index(value = ["userId", "syncStatus"])
    ]
)
data class SavingsGoalEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val name: String,
    val targetAmountMinor: Long = 0L,
    val targetDate: String = "",
    val openingSavedAmountMinor: Long = 0L,
    val status: String = "ACTIVE",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val isDeleted: Boolean = false
)
