package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo

/**
 * Room entity representing an income record stored locally for offline caching and synchronization.
 * Preserves all basic (Member 1) and advanced currency/crypto/project (Member 2) domain fields,
 * while adding sync metadata for offline-first support (Member 3).
 */
@Entity(
    tableName = "incomes",
    primaryKeys = ["userId", "id"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "receivedDate"]),
        Index(value = ["userId", "syncStatus"]),
        Index(value = ["userId", "isDeleted"])
    ]
)
data class IncomeEntity(
    val id: String,
    val userId: String,
    val amountMinor: Long = 0L,
    val currency: String = "LKR",
    val source: String = "Salary",
    val receivedDate: String = "",
    val note: String = "",

    // Member 2 advanced fields
    val amountLkrMinor: Long? = null,
    val exchangeRate: String? = null,
    val exchangeRateDate: String? = null,
    val paymentStatus: String = "Received",
    val projectName: String? = null,
    val cryptoType: String? = null,
    val cryptoAsset: String? = null,

    // Timestamps
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,

    // Offline sync metadata
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "''") val revision: String = ""
)
