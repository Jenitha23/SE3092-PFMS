package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Pure calculation use case implementing FR-26 (Trailing 3-Month Net Savings):
 * Computes the 3-calendar-month average net savings (income - expenses) across the 3
 * completed preceding calendar months (M-3, M-2, M-1).
 *
 * Rules:
 * - Operates strictly across 3 full calendar months.
 * - Months with no income or expenses evaluate to 0L and are included in the denominator (always divides by 3.0).
 * - Proper mathematical rounding ([roundToLong]) is applied.
 */
class CalculateTrailingNetSavingsAverageUseCase @Inject constructor(
    private val calculateMonthlyNetSavingsUseCase: CalculateMonthlyNetSavingsUseCase
) {

    data class Result(
        val averageMinor: Long,
        val totalMinor: Long,
        val monthsEvaluated: List<String>
    )

    operator fun invoke(
        incomes: List<Income>,
        expenses: List<Expense>,
        referenceMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo"))
    ): Result {
        val months = listOf(
            referenceMonth.minusMonths(3),
            referenceMonth.minusMonths(2),
            referenceMonth.minusMonths(1)
        )

        var totalNetSavings = 0L
        val monthKeys = mutableListOf<String>()

        for (month in months) {
            monthKeys.add(month.toString())
            val monthNet = calculateMonthlyNetSavingsUseCase(incomes, expenses, month)
            totalNetSavings += monthNet
        }

        val averageMinor = (totalNetSavings.toDouble() / 3.0).roundToLong()

        return Result(
            averageMinor = averageMinor,
            totalMinor = totalNetSavings,
            monthsEvaluated = monthKeys
        )
    }
}
