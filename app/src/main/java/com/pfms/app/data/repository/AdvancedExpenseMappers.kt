package com.pfms.app.data.repository

import com.pfms.app.data.local.entity.ExpenseCategoryEntity
import com.pfms.app.data.local.entity.RecurringExpenseEntity
import com.pfms.app.domain.model.*

private inline fun <reified T : Enum<T>> enumValue(value: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name.equals(value, true) } ?: fallback

internal fun ExpenseCategory.toEntity(userId: String) = ExpenseCategoryEntity(
    id, userId, name, classification.name, custom, archived
)

internal fun ExpenseCategoryEntity.toDomain() = ExpenseCategory(
    id, name, enumValue(classification, ExpenseClassification.DISCRETIONARY), isCustom, isArchived
)

internal fun RecurringExpense.toEntity(userId: String) = RecurringExpenseEntity(
    id, userId, title, categoryId, categoryName, classification.name, amountMinor,
    paymentMethod.name, recurrence.name, nextDueDate, active, subscription, flag.name, reminderDate
)

internal fun RecurringExpenseEntity.toDomain() = RecurringExpense(
    id, title, categoryId, categoryName, enumValue(classification, ExpenseClassification.COMMITTED),
    amountMinor, enumValue(paymentMethod, ExpensePaymentMethod.CASH),
    enumValue(recurrence, Recurrence.MONTHLY), nextDueDate, isActive, isSubscription,
    enumValue(flag, SubscriptionFlag.NONE), reminderDate
)
