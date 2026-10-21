package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Pure calculation use case implementing FR-26 (General Monthly Net Savings):
 * Calculates net savings across the user's total financial flows for a given calendar month:
 *
 * Formula:
 * monthlyNetSavings = monthly realized income - monthly expenses
 *
 * Rules:
 * - Realized income is computed via [GetMonthlyIncomeSummaryUseCase] adhering to all Member 2 realized
 *   rules (excluding unrealized freelance, factoring crypto gains/losses, USD-to-LKR conversions).
 * - Expenses are summed across all categories for the selected month.
 * - If expense list is empty (e.g. before expense integration or when user has no expenses):
 *   monthly expenses = 0L.
 * - Returns a signed Long (may be negative if expenses exceed realized income).
 */
class CalculateMonthlyNetSavingsUseCase @Inject constructor(
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase
) {

    operator fun invoke(
        incomes: List<Income>,
        expenses: List<Expense>,
        targetMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo"))
    ): Long {
        val incomeSummary = getMonthlyIncomeSummaryUseCase(incomes, targetMonth)
        val realizedIncomeMinor = incomeSummary.totalRealizedMinor

        var totalExpensesMinor = 0L
        for (expense in expenses) {
            val expenseMonth = IncomeRealizedCalculator.parseYearMonth(expense.date)
            if (expenseMonth == targetMonth) {
                totalExpensesMinor += expense.amountMinor
            }
        }

        return realizedIncomeMinor - totalExpensesMinor
    }
}
