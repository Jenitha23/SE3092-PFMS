package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "crypto_trades", primaryKeys = ["userId", "id"], indices = [
    Index(value = ["userId", "asset", "date"]),
    Index(value = ["userId", "syncStatus"])
])
data class CryptoTradeEntity(
    val id: String,
    val userId: String,
    val asset: String,
    val side: String,
    val quantity: String,
    val grossLkrMinor: Long,
    val feeLkrMinor: Long,
    val date: String,
    val createdAt: Long,
    val originalCurrency: String,
    val originalGrossMinor: Long,
    val originalFeeMinor: Long,
    val exchangeRate: String?,
    val note: String,
    val realizedLkrMinor: Long?,
    val syncStatus: SyncStatus = SyncStatus.PENDING_INSERT
)
