package com.pfms.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import com.pfms.app.domain.model.*

@Entity(tableName = "expenses", primaryKeys = ["userId", "id"], indices = [Index("userId")])
data class ExpenseEntity(
    val userId: String,
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val categoryId: String,
    val categoryName: String,
    val classification: String,
    val paymentMethod: String,
    val date: String,
    val merchant: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: String = "SYNCED",
    val isDeleted: Boolean = false,
    val revision: String = "",
    val allowDuplicate: Boolean = false,
    val needsConfirmation: Boolean = false
) {
    fun toDomain() = Expense(id, userId, amountMinor, currency, categoryId, categoryName,
        ExpenseClassification.valueOf(classification), ExpensePaymentMethod.valueOf(paymentMethod),
        date, merchant, note, createdAt, updatedAt)
}

fun Expense.toEntity() = ExpenseEntity(userId, id, amountMinor, currency, categoryId,
    categoryName, classification.name, paymentMethod.name, date, merchant, note, createdAt, updatedAt)
