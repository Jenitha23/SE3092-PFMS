package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing recurring salary template configuration cached locally.
 * Preserves all domain fields while adding sync metadata for offline-first support.
 */
@Entity(
    tableName = "salary_templates",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["userId", "isDeleted"])
    ]
)
data class SalaryTemplateEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val amountMinor: Long = 0L,
    val currency: String = "LKR",
    val paymentDay: Int = 25,
    val isEnabled: Boolean = true,
    val lastConfirmedMonth: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,

    // Offline sync metadata
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val isDeleted: Boolean = false
)
