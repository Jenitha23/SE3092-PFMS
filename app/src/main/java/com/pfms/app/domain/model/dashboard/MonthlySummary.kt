package com.pfms.app.domain.model.dashboard

import java.time.YearMonth

/** One slice of the category chart (FR-30). [percent] is 0-100, rounded half-up to 1 dp. */
data class CategorySpend(
    val categoryName: String,
    val amountMinor: Long,
    val percent: Double
)

/** Committed vs Discretionary split bar (FR-31). Percentages are 0 when nothing was spent. */
data class ClassificationSplit(
    val committedMinor: Long,
    val discretionaryMinor: Long,
    val committedPercent: Double,
    val discretionaryPercent: Double
) {
    val totalMinor: Long get() = committedMinor + discretionaryMinor
}

/** Everything the dashboard header card needs for one month (FR-29). */
data class MonthlySummary(
    val month: YearMonth,
    val totalIncomeMinor: Long,
    val totalExpenseMinor: Long,
    val incomeCount: Int,
    val expenseCount: Int,
    val categories: List<CategorySpend>,
    val split: ClassificationSplit
) {
    val netSavingsMinor: Long get() = totalIncomeMinor - totalExpenseMinor
    val isSurplus: Boolean get() = netSavingsMinor >= 0L
    val hasData: Boolean get() = incomeCount > 0 || expenseCount > 0
}

/** Spending insight card (FR-32). */
data class SpendingInsights(
    val topCategories: List<CategorySpend>,
    val largestCategory: CategorySpend?,
    val discretionaryPercent: Double,
    val largestExpenses: List<Transaction>
)

/** One bar pair of the six-month income-versus-expense trend (FR-32). */
data class MonthlyTrendPoint(
    val month: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}
