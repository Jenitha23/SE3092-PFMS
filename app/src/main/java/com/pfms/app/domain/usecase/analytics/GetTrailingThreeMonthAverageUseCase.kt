package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * Calculates the actual trailing 3-calendar-month average realized income.
 *
 * Interpretation & Specification:
 * - Operates across 3 full calendar months (not individual transactions).
 * - By default, [includeCurrentMonth] is false: it uses the 3 immediately preceding
 *   completed calendar months (M-3, M-2, M-1) so that an incomplete ongoing month
 *   does not artificially depress the average.
 * - If [includeCurrentMonth] is true: it evaluates (M-2, M-1, M).
 * - Average is calculated as total realized income across the 3 calendar months divided by 3.0.
 */
class GetTrailingThreeMonthAverageUseCase @Inject constructor(
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase
) {

    data class Result(
        val averageMinor: Long,
        val totalMinor: Long,
        val monthsEvaluated: List<String>
    )

    operator fun invoke(
        incomes: List<Income>,
        referenceMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo")),
        includeCurrentMonth: Boolean = false
    ): Result {
        val months = if (includeCurrentMonth) {
            listOf(
                referenceMonth.minusMonths(2),
                referenceMonth.minusMonths(1),
                referenceMonth
            )
        } else {
            listOf(
                referenceMonth.minusMonths(3),
                referenceMonth.minusMonths(2),
                referenceMonth.minusMonths(1)
            )
        }

        var totalMinor = 0L
        val monthKeys = mutableListOf<String>()

        for (month in months) {
            monthKeys.add(month.toString())
            val summary = getMonthlyIncomeSummaryUseCase(incomes, month)
            totalMinor += summary.totalRealizedMinor
        }

        // 3-calendar-month average with proper rounding
        val averageMinor = (totalMinor.toDouble() / 3.0).roundToLong()

        return Result(
            averageMinor = averageMinor,
            totalMinor = totalMinor,
            monthsEvaluated = monthKeys
        )
    }
}
