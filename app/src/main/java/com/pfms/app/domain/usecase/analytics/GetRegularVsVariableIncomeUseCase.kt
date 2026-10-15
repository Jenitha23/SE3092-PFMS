package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.MonthlyIncomeSummary
import com.pfms.app.domain.model.analytics.RegularVariableIncome
import java.time.YearMonth
import javax.inject.Inject

/**
 * Classifies realized income into Regular (Salary) vs Variable (Freelance, AdSense, Crypto, Other).
 *
 * Handles:
 * - Zero total safely (returns 0.0% for both, preventing NaN / division by zero).
 * - Crypto net losses correctly.
 */
class GetRegularVsVariableIncomeUseCase @Inject constructor(
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase
) {

    operator fun invoke(summary: MonthlyIncomeSummary): RegularVariableIncome {
        val regular = summary.salaryMinor
        val variable = summary.freelanceMinor + summary.adsenseMinor + summary.cryptoNetMinor + summary.otherMinor
        val total = regular + variable

        val (regPct, varPct) = calculatePercentages(regular, variable, total)

        return RegularVariableIncome(
            regularMinor = regular,
            variableMinor = variable,
            totalMinor = total,
            regularPercentage = regPct,
            variablePercentage = varPct
        )
    }

    operator fun invoke(incomes: List<Income>, targetMonth: YearMonth? = null): RegularVariableIncome {
        val relevantIncomes = if (targetMonth != null) {
            incomes.filter { IncomeRealizedCalculator.matchesMonth(it, targetMonth) }
        } else {
            incomes
        }

        var regular = 0L
        var variable = 0L

        for (income in relevantIncomes) {
            val signedAmount = IncomeRealizedCalculator.getRealizedSignedAmountMinor(income)
            if (income.source.equals(IncomeRealizedCalculator.SOURCE_SALARY, ignoreCase = true)) {
                regular += signedAmount
            } else {
                variable += signedAmount
            }
        }

        val total = regular + variable
        val (regPct, varPct) = calculatePercentages(regular, variable, total)

        return RegularVariableIncome(
            regularMinor = regular,
            variableMinor = variable,
            totalMinor = total,
            regularPercentage = regPct,
            variablePercentage = varPct
        )
    }

    private fun calculatePercentages(regular: Long, variable: Long, total: Long): Pair<Double, Double> {
        if (total <= 0L) {
            return Pair(0.0, 0.0)
        }
        val totalDouble = total.toDouble()
        val regPct = (regular.toDouble() / totalDouble) * 100.0
        val varPct = (variable.toDouble() / totalDouble) * 100.0
        return Pair(regPct, varPct)
    }
}
