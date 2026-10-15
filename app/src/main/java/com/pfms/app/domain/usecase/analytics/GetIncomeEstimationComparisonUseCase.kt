package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.EstimationStatus
import com.pfms.app.domain.model.analytics.IncomeEstimationComparison
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Compares the user's estimated monthly income against their actual trailing 3-calendar-month average.
 *
 * Handles:
 * - estimate == 0 safely (variance 0.0, status ON_TARGET if actual is also 0, or ABOVE_ESTIMATE).
 * - months with zero income.
 * - negative crypto net values.
 */
class GetIncomeEstimationComparisonUseCase @Inject constructor(
    private val getTrailingThreeMonthAverageUseCase: GetTrailingThreeMonthAverageUseCase
) {

    operator fun invoke(
        estimatedMonthlyIncomeMinor: Long,
        incomes: List<Income>,
        referenceMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo")),
        includeCurrentMonth: Boolean = false
    ): IncomeEstimationComparison {
        val trailingResult = getTrailingThreeMonthAverageUseCase(
            incomes = incomes,
            referenceMonth = referenceMonth,
            includeCurrentMonth = includeCurrentMonth
        )

        val actualAverage = trailingResult.averageMinor
        val difference = actualAverage - estimatedMonthlyIncomeMinor

        val variancePercentage = if (estimatedMonthlyIncomeMinor > 0L) {
            (difference.toDouble() / estimatedMonthlyIncomeMinor.toDouble()) * 100.0
        } else {
            0.0
        }

        val status = when {
            difference > 0L -> EstimationStatus.ABOVE_ESTIMATE
            difference < 0L -> EstimationStatus.BELOW_ESTIMATE
            else -> EstimationStatus.ON_TARGET
        }

        return IncomeEstimationComparison(
            estimatedMonthlyIncomeMinor = estimatedMonthlyIncomeMinor,
            trailingThreeMonthAverageMinor = actualAverage,
            differenceMinor = difference,
            variancePercentage = variancePercentage,
            status = status,
            monthsEvaluated = trailingResult.monthsEvaluated
        )
    }
}
