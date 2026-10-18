package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.dashboard.SpendingInsights

/** FR-32: top three categories, discretionary share, largest category and five largest expenses. */
object SpendingInsightsCalculator {

    fun build(monthExpenses: List<Expense>): SpendingInsights {
        val categories = MonthlyFinanceCalculator.categoryBreakdown(monthExpenses)
        val split = MonthlyFinanceCalculator.split(monthExpenses)
        val largest = monthExpenses
            .sortedWith(
                compareByDescending<Expense> { it.amountMinor }
                    .thenByDescending { it.date }
                    .thenBy { it.id }
            )
            .take(5)
            .map { it.toTransaction() }
        return SpendingInsights(
            topCategories = categories.take(3),
            largestCategory = categories.firstOrNull(),
            discretionaryPercent = split.discretionaryPercent,
            largestExpenses = largest
        )
    }
}
