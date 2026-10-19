package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.dashboard.CategorySpend
import com.pfms.app.domain.model.dashboard.ClassificationSplit
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.MonthlyTrendPoint
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

/**
 * Single home for the dashboard / analytics money rules (NFR-17): the dashboard card, the
 * category chart, the split bar and the trend all call these functions, so they cannot disagree.
 * Pure Kotlin, no Android or Firebase types, covered by unit tests (NFR-19).
 */
object MonthlyFinanceCalculator {

    const val UNCATEGORISED = "Uncategorised"

    fun summarize(incomes: List<Income>, expenses: List<Expense>, month: YearMonth): MonthlySummary {
        val monthIncomes = incomes.filter { IncomeRealizedCalculator.matchesMonth(it, month) }
        val monthExpenses = expenses.filter { IncomeRealizedCalculator.parseYearMonth(it.date) == month }
        return MonthlySummary(
            month = month,
            totalIncomeMinor = monthIncomes.sumOf { IncomeRealizedCalculator.getRealizedSignedAmountMinor(it) },
            totalExpenseMinor = monthExpenses.sumOf { it.amountMinor },
            incomeCount = monthIncomes.size,
            expenseCount = monthExpenses.size,
            categories = categoryBreakdown(monthExpenses),
            split = split(monthExpenses)
        )
    }

    /** Largest to smallest. The amounts always add up to the total expenses (NFR-16). */
    fun categoryBreakdown(expenses: List<Expense>): List<CategorySpend> {
        val total = expenses.sumOf { it.amountMinor }
        return expenses
            .groupBy { it.categoryName.trim().ifBlank { UNCATEGORISED } }
            .map { (name, rows) ->
                val amount = rows.sumOf { it.amountMinor }
                CategorySpend(name, amount, percentOf(amount, total))
            }
            .sortedWith(compareByDescending<CategorySpend> { it.amountMinor }.thenBy { it.categoryName })
    }

    fun split(expenses: List<Expense>): ClassificationSplit {
        val committed = expenses.filter { it.classification == ExpenseClassification.COMMITTED }
            .sumOf { it.amountMinor }
        val discretionary = expenses.filter { it.classification == ExpenseClassification.DISCRETIONARY }
            .sumOf { it.amountMinor }
        val total = committed + discretionary
        return ClassificationSplit(
            committedMinor = committed,
            discretionaryMinor = discretionary,
            committedPercent = percentOf(committed, total),
            discretionaryPercent = percentOf(discretionary, total)
        )
    }

    /** [months] points ending at [endMonth], oldest first. Months with no data are zero, not missing. */
    fun trend(
        incomes: List<Income>,
        expenses: List<Expense>,
        endMonth: YearMonth,
        months: Int = 6
    ): List<MonthlyTrendPoint> {
        val incomeByMonth = HashMap<YearMonth, Long>()
        for (income in incomes) {
            val ym = IncomeRealizedCalculator.parseYearMonth(income.receivedDate) ?: continue
            incomeByMonth[ym] = (incomeByMonth[ym] ?: 0L) +
                    IncomeRealizedCalculator.getRealizedSignedAmountMinor(income)
        }
        val expenseByMonth = HashMap<YearMonth, Long>()
        for (expense in expenses) {
            val ym = IncomeRealizedCalculator.parseYearMonth(expense.date) ?: continue
            expenseByMonth[ym] = (expenseByMonth[ym] ?: 0L) + expense.amountMinor
        }
        return (months - 1 downTo 0).map { back ->
            val ym = endMonth.minusMonths(back.toLong())
            MonthlyTrendPoint(ym, incomeByMonth[ym] ?: 0L, expenseByMonth[ym] ?: 0L)
        }
    }

    /** part/whole as 0-100 rounded half-up to 1 dp. Never divides by zero (FR-34). */
    fun percentOf(part: Long, whole: Long): Double {
        if (whole <= 0L || part <= 0L) return 0.0
        return BigDecimal(part)
            .multiply(BigDecimal(100))
            .divide(BigDecimal(whole), 1, RoundingMode.HALF_UP)
            .toDouble()
    }
}
