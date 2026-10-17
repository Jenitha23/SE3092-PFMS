package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "recurring_expenses",
    primaryKeys = ["userId", "id"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isActive"]),
        Index(value = ["userId", "isSubscription"])
    ]
)
data class RecurringExpenseEntity(
    val id: String,
    val userId: String,
    val title: String,
    val categoryId: String,
    val categoryName: String,
    val classification: String,
    val amountMinor: Long,
    val paymentMethod: String,
    val recurrence: String,
    val nextDueDate: String,
    val isActive: Boolean,
    val isSubscription: Boolean,
    val flag: String,
    val reminderDate: String,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
)
