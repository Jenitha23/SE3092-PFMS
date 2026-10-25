package com.pfms.app.viewmodel

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CategoryEditorState(
    val name: String = "",
    val classification: ExpenseClassification = ExpenseClassification.DISCRETIONARY,
    val original: ExpenseCategory? = null,
    val error: String? = null
)

data class RecurringEditorState(
    val original: RecurringExpense,
    val title: String = original.title,
    val amount: String = if (original.amountMinor > 0) BigDecimal.valueOf(original.amountMinor, 2).toPlainString() else "",
    val categoryId: String = original.categoryId,
    val payment: ExpensePaymentMethod = original.paymentMethod,
    val recurrence: Recurrence = original.recurrence,
    val due: String = original.nextDueDate,
    val reminder: String = original.reminderDate,
    val flag: SubscriptionFlag = original.flag,
    val editing: Boolean = false,
    val menu: String? = null,
    val error: String? = null
) {
    val dueLabel: String get() = advancedDate(due)
    val reminderLabel: String get() = if (reminder.isBlank()) "No reminder" else advancedDate(reminder)
    val paymentLabel: String get() = advancedPayment(payment)
    val dueMillis: Long? get() = advancedDateMillis(due)
    val reminderMillis: Long? get() = advancedDateMillis(reminder)
    val saveLabel: String get() = if (editing) "Update Template" else "Save Template"
}

data class RecurringRecordState(
    val template: RecurringExpense,
    val amount: String,
    val frequency: String,
    val details: String,
    val dueLabel: String,
    val reviewLabel: String?,
    val status: String,
    val due: Boolean,
    val review: Boolean,
    val dueSoon: Boolean = false,
    val relativeDate: String = ""
)

data class RecurringFilterState(val key: String, val label: String, val count: Int)

data class AdvancedExpenseControls(
    val archived: Boolean = false,
    val categoryEditor: CategoryEditorState = CategoryEditorState(),
    val archiveTarget: ExpenseCategory? = null,
    val editor: RecurringEditorState? = null,
    val deleteTarget: RecurringExpense? = null,
    val confirmTarget: RecurringExpense? = null,
    val confirmAmount: String = "",
    val confirmError: String? = null,
    val menu: String? = null,
    val recurringFilter: String = "all"
)

data class AdvancedExpenseScreenState(
    val controls: AdvancedExpenseControls = AdvancedExpenseControls(),
    val activeCategories: List<ExpenseCategory> = emptyList(),
    val archivedCategories: List<ExpenseCategory> = emptyList(),
    val recurring: List<RecurringRecordState> = emptyList(),
    val subscriptions: List<RecurringRecordState> = emptyList(),
    val monthlyTotal: String = "LKR 0.00",
    val activeRecurring: Int = 0,
    val dueRecurring: Int = 0,
    val activeSubscriptions: Int = 0,
    val dueSubscriptions: Int = 0,
    val reviewSubscriptions: Int = 0,
    val customCount: Int = 0,
    val defaultCount: Int = 0,
    val selectedCategory: ExpenseCategory? = null,
    val recurringMonthlyTotal: String = "LKR 0.00",
    val recurringFilters: List<RecurringFilterState> = emptyList(),
    val filteredRecurring: List<RecurringRecordState> = emptyList(),
    val dueSoonRecurring: Int = 0,
    val nextRecurringPayment: String? = null
)

internal fun advancedExpenseScreenState(
    categories: List<ExpenseCategory>, templates: List<RecurringExpense>, monthlyMinor: Long?, controls: AdvancedExpenseControls
): AdvancedExpenseScreenState {
    val active = categories.filterNot { it.archived }.sortedWith(compareByDescending<ExpenseCategory> { it.custom }.thenBy { it.name.lowercase(Locale.ROOT) })
    val records = templates.map { template ->
        RecurringRecordState(template, "LKR ${financeMoney(template.amountMinor)}", advancedFrequency(template.recurrence),
            listOf(template.categoryName.ifBlank { "Uncategorized" }, advancedPayment(template.paymentMethod)).joinToString(" • "),
            advancedDate(template.nextDueDate), when {
                template.reminderDate.isNotBlank() -> "Review · ${advancedDate(template.reminderDate)}"
                template.flag == SubscriptionFlag.REVIEW -> "Flagged for review"
                else -> null
            },
            when { template.flag == SubscriptionFlag.CANCEL -> "Cancelled"; !template.active -> "Paused"; else -> "Active" },
            RecurringExpenseCalculator.isDue(template), RecurringExpenseCalculator.isReviewDue(template),
            RecurringExpenseCalculator.isDueSoon(template), runCatching {
                val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE), LocalDate.parse(template.nextDueDate))
                when { days < 0 -> "Overdue"; days == 0L -> "Today"; days == 1L -> "Tomorrow"; else -> "In $days days" }
            }.getOrDefault(""))
    }.sortedWith(compareByDescending<RecurringRecordState> { it.due || it.review }.thenByDescending { it.template.active }.thenBy { it.template.nextDueDate }.thenBy { it.template.title.lowercase(Locale.ROOT) })
    val recurring = records.filterNot { it.template.subscription }
    val subscriptions = records.filter { it.template.subscription }
    return AdvancedExpenseScreenState(controls, active, categories.filter { it.archived }.sortedBy { it.name.lowercase(Locale.ROOT) },
        recurring, subscriptions, monthlyMinor?.let { "LKR ${financeMoney(it)}" } ?: "Unavailable",
        recurring.count { it.template.active && it.template.flag != SubscriptionFlag.CANCEL }, recurring.count { it.due },
        subscriptions.count { it.template.active && it.template.flag != SubscriptionFlag.CANCEL }, subscriptions.count { it.due },
        subscriptions.count { it.review }, active.count { it.custom }, active.count { !it.custom },
        active.firstOrNull { it.id == controls.editor?.categoryId },
        runCatching { "LKR ${financeMoney(RecurringExpenseCalculator.calculateMonthlyScheduledTotal(templates))}" }.getOrDefault("Unavailable"),
        listOf(RecurringFilterState("all", "All", recurring.size), RecurringFilterState("due", "Due soon", recurring.count { it.dueSoon }),
            RecurringFilterState("monthly", "Monthly", recurring.count { it.template.recurrence == Recurrence.MONTHLY })) +
            recurring.groupBy { it.template.categoryId }.map { (id, items) -> RecurringFilterState("category:$id", items.first().template.categoryName.ifBlank { "Uncategorized" }, items.size) },
        recurring.filter { row -> when (controls.recurringFilter) {
            "all" -> true; "due" -> row.dueSoon; "monthly" -> row.template.recurrence == Recurrence.MONTHLY
            else -> "category:${row.template.categoryId}" == controls.recurringFilter
        } }, recurring.count { it.dueSoon }, recurring.filter { it.template.active && !it.due }.minByOrNull { it.template.nextDueDate }?.let { "Next payment · ${it.dueLabel}" })
}

internal fun advancedFrequency(value: Recurrence): String = when (value) {
    Recurrence.DAILY -> "Daily"; Recurrence.WEEKLY -> "Weekly"; Recurrence.MONTHLY -> "Monthly"
}

internal fun advancedPayment(value: ExpensePaymentMethod): String = when (value) {
    ExpensePaymentMethod.CASH -> "Cash"
    ExpensePaymentMethod.CARD -> "Card"
    ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT -> "Bank transfer / Auto-debit"
    ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM -> "Digital wallet / Platform"
}

private fun advancedDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
}.getOrDefault(value.ifBlank { "Choose date" })

private fun advancedDateMillis(value: String): Long? = runCatching {
    LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}.getOrNull()
