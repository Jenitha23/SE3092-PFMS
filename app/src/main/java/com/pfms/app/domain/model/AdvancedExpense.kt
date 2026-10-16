package com.pfms.app.domain.model

data class ExpenseCategory(
    val id: String = "", val name: String = "", val classification: ExpenseClassification = ExpenseClassification.DISCRETIONARY,
    val custom: Boolean = false, val archived: Boolean = false
)

enum class Recurrence { DAILY, WEEKLY, MONTHLY }
enum class SubscriptionFlag { NONE, REVIEW, CANCEL }

data class RecurringExpense(
    val id: String = "", val title: String = "", val categoryId: String = "",
    val categoryName: String = "", val classification: ExpenseClassification = ExpenseClassification.COMMITTED,
    val amountMinor: Long = 0, val paymentMethod: ExpensePaymentMethod = ExpensePaymentMethod.CASH,
    val recurrence: Recurrence = Recurrence.MONTHLY, val nextDueDate: String = "",
    val active: Boolean = true, val subscription: Boolean = false,
    val flag: SubscriptionFlag = SubscriptionFlag.NONE, val reminderDate: String = ""
)
