package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.MonthlyTrendPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

data class DashboardOverview(
    val summary: MonthlySummary,
    val trend: List<MonthlyTrendPoint>,
    val elapsedDays: Int,
    val daysRemaining: Int,
    val isCurrentMonth: Boolean,
    val dailySpendMinor: Long,
    val dailyAverageHistory: List<Long>,
    val dailySpendChangePercent: Double?,
    val inflowChangePercent: Double?,
    val coveragePercent: Double?,
    val expenseToIncomePercent: Double?,
    val historicalAverageNetMinor: Long
)

/** Recorded cash flow only: no assumptions about account balances, reserves or budgets. */
object DashboardOverviewCalculator {
    const val TREND_MONTHS = 5

    fun calculate(incomes: List<Income>, expenses: List<Expense>, month: YearMonth,
        today: LocalDate = AppClock.today()): DashboardOverview {
        val summary = MonthlyFinanceCalculator.summarize(incomes, expenses, month)
        val trend = MonthlyFinanceCalculator.trend(incomes, expenses, month, TREND_MONTHS)
        val current = month == YearMonth.from(today)
        val elapsed = when {
            current -> today.dayOfMonth
            month.isBefore(YearMonth.from(today)) -> month.lengthOfMonth()
            else -> 0
        }
        val datedExpenses = expenses.mapNotNull { expense ->
            val date = runCatching { LocalDate.parse(expense.date) }.getOrNull()
            if (date != null && YearMonth.from(date) == month) date to expense.amountMinor else null
        }
        val spentSoFar = datedExpenses.filter { it.first.dayOfMonth <= elapsed }.sumOf { it.second }
        val dailyAverage = divide(spentSoFar, elapsed)
        val previous = trend[trend.lastIndex - 1]
        val previousDaily = divide(previous.expenseMinor, previous.month.lengthOfMonth())
        val history = if (elapsed == 0) emptyList() else ((elapsed - 6).coerceAtLeast(1)..elapsed).map { day ->
            divide(datedExpenses.filter { it.first.dayOfMonth <= day }.sumOf { it.second }, day)
        }
        val historicTotal = trend.dropLast(1).fold(BigDecimal.ZERO) { total, point ->
            total + BigDecimal.valueOf(point.netMinor)
        }
        return DashboardOverview(
            summary = summary, trend = trend, elapsedDays = elapsed,
            daysRemaining = if (current) month.lengthOfMonth() - elapsed else 0,
            isCurrentMonth = current, dailySpendMinor = dailyAverage,
            dailyAverageHistory = history,
            dailySpendChangePercent = change(dailyAverage, previousDaily),
            inflowChangePercent = change(summary.totalIncomeMinor, previous.incomeMinor),
            coveragePercent = if (summary.totalExpenseMinor > 0L)
                MonthlyFinanceCalculator.percentOf(summary.totalIncomeMinor, summary.totalExpenseMinor) else null,
            expenseToIncomePercent = if (summary.totalIncomeMinor > 0L)
                MonthlyFinanceCalculator.percentOf(summary.totalExpenseMinor, summary.totalIncomeMinor) else null,
            historicalAverageNetMinor = historicTotal.divide(BigDecimal.valueOf((TREND_MONTHS - 1).toLong()),
                0, RoundingMode.HALF_UP).longValueExact()
        )
    }

    private fun divide(amount: Long, days: Int): Long = if (days <= 0) 0L else
        BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(days.toLong()), 0, RoundingMode.HALF_UP).longValueExact()

    private fun change(current: Long, previous: Long): Double? = if (previous <= 0L) null else
        (BigDecimal.valueOf(current) - BigDecimal.valueOf(previous)).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP).toDouble()
}
