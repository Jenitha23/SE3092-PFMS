package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "expense_categories",
    primaryKeys = ["userId", "id"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isArchived"])
    ]
)
data class ExpenseCategoryEntity(
    val id: String,
    val userId: String,
    val name: String,
    val classification: String,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
)
