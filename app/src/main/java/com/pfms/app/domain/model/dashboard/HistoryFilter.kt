package com.pfms.app.domain.model.dashboard

import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import java.time.LocalDate

enum class TransactionTypeFilter { ALL, INCOME, EXPENSE }

/**
 * Filters for the transaction history (FR-33).
 *
 * Income-only filters ([incomeSource]) never match expenses, and expense-only filters
 * ([expenseCategory], [paymentMethod], [classification]) never match income. Setting one of each
 * kind therefore matches nothing, which is why the UI only offers a group of chips when the
 * selected [type] can use it.
 */
data class HistoryFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val type: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val incomeSource: String? = null,
    val expenseCategory: String? = null,
    val paymentMethod: ExpensePaymentMethod? = null,
    val classification: ExpenseClassification? = null
) {
    val activeCount: Int
        get() = listOf(
            startDate != null || endDate != null,
            type != TransactionTypeFilter.ALL,
            incomeSource != null,
            expenseCategory != null,
            paymentMethod != null,
            classification != null
        ).count { it }

    val isActive: Boolean get() = activeCount > 0
}

/** "N matching transactions and their total amount" (FR-33). */
data class HistorySummary(
    val count: Int,
    val incomeMinor: Long,
    val expenseMinor: Long
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}
