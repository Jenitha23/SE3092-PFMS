package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.dashboard.HistoryFilter
import com.pfms.app.domain.model.dashboard.HistorySummary
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionType
import com.pfms.app.domain.model.dashboard.TransactionTypeFilter
import java.time.LocalDate

/** FR-33: merge, order, filter and total the transaction history. Pure and unit tested. */
object TransactionHistoryCalculator {

    /** Rows loaded per "page" while scrolling (incremental loading, FR-33 / NFR-18). */
    const val PAGE_SIZE = 30

    /** Newest first: date, then creation time, then id so the order is stable. */
    fun merge(incomes: List<Income>, expenses: List<Expense>): List<Transaction> =
        (incomes.map { it.toTransaction() } + expenses.map { it.toTransaction() })
            .sortedWith(
                compareByDescending<Transaction> { it.date }
                    .thenByDescending { it.createdAt }
                    .thenByDescending { it.id }
            )

    fun filter(all: List<Transaction>, filter: HistoryFilter): List<Transaction> {
        if (!filter.isActive) return all
        return all.filter { matches(it, filter) }
    }

    fun summarize(rows: List<Transaction>): HistorySummary {
        var income = 0L
        var expense = 0L
        for (row in rows) {
            if (row.type == TransactionType.INCOME) income += row.netEffectMinor
            else expense += row.amountMinor
        }
        return HistorySummary(count = rows.size, incomeMinor = income, expenseMinor = expense)
    }

    private fun matches(row: Transaction, f: HistoryFilter): Boolean {
        when (f.type) {
            TransactionTypeFilter.INCOME -> if (row.type != TransactionType.INCOME) return false
            TransactionTypeFilter.EXPENSE -> if (row.type != TransactionType.EXPENSE) return false
            TransactionTypeFilter.ALL -> Unit
        }
        if (f.startDate != null || f.endDate != null) {
            val date = parseDate(row.date) ?: return false
            if (f.startDate != null && date.isBefore(f.startDate)) return false
            if (f.endDate != null && date.isAfter(f.endDate)) return false
        }
        if (f.incomeSource != null &&
            !(row.type == TransactionType.INCOME && row.incomeSource == f.incomeSource)
        ) return false
        if (f.expenseCategory != null &&
            !(row.type == TransactionType.EXPENSE && row.expenseCategory == f.expenseCategory)
        ) return false
        if (f.paymentMethod != null &&
            !(row.type == TransactionType.EXPENSE && row.paymentMethod == f.paymentMethod)
        ) return false
        if (f.classification != null &&
            !(row.type == TransactionType.EXPENSE && row.classification == f.classification)
        ) return false
        return true
    }

    private fun parseDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim().take(10)) }.getOrNull()
}
