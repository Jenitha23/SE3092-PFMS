package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.MonthlyIncomeSummary
import java.time.YearMonth
import javax.inject.Inject

/**
 * Calculates realized income breakdown by source for a given calendar month.
 *
 * Rules:
 * - Realized items only: expected freelance excluded.
 * - Crypto losses subtract from cryptoNet and total.
 * - USD converted amounts used directly from stored amountLkrMinor.
 */
class GetMonthlyIncomeSummaryUseCase @Inject constructor() {

    operator fun invoke(incomes: List<Income>, targetMonth: YearMonth): MonthlyIncomeSummary {
        val monthKey = targetMonth.toString()

        var salaryMinor = 0L
        var freelanceMinor = 0L
        var adsenseMinor = 0L
        var cryptoNetMinor = 0L
        var otherMinor = 0L

        val monthlyIncomes = incomes.filter { IncomeRealizedCalculator.matchesMonth(it, targetMonth) }

        for (income in monthlyIncomes) {
            if (!IncomeRealizedCalculator.isRealized(income)) {
                // Expected freelance or non-realized income excluded
                continue
            }

            val amount = IncomeRealizedCalculator.getEffectiveLkrMinor(income)

            when (income.source) {
                IncomeRealizedCalculator.SOURCE_SALARY -> {
                    salaryMinor += amount
                }
                IncomeRealizedCalculator.SOURCE_FREELANCE -> {
                    freelanceMinor += amount
                }
                IncomeRealizedCalculator.SOURCE_GOOGLE_ADSENSE -> {
                    adsenseMinor += amount
                }
                IncomeRealizedCalculator.SOURCE_CRYPTO -> {
                    if (income.cryptoType.equals(IncomeRealizedCalculator.CRYPTO_LOSS, ignoreCase = true)) {
                        cryptoNetMinor -= amount
                    } else {
                        cryptoNetMinor += amount
                    }
                }
                else -> {
                    otherMinor += amount
                }
            }
        }

        val totalRealized = salaryMinor + freelanceMinor + adsenseMinor + cryptoNetMinor + otherMinor

        return MonthlyIncomeSummary(
            yearMonth = monthKey,
            totalRealizedMinor = totalRealized,
            salaryMinor = salaryMinor,
            freelanceMinor = freelanceMinor,
            adsenseMinor = adsenseMinor,
            cryptoNetMinor = cryptoNetMinor,
            otherMinor = otherMinor
        )
    }

    operator fun invoke(incomes: List<Income>, targetMonthKey: String): MonthlyIncomeSummary {
        val ym = IncomeRealizedCalculator.parseYearMonth(targetMonthKey) ?: YearMonth.now()
        return invoke(incomes, ym)
    }
}
