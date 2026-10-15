package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.IncomeTrendPoint
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Calculates continuous month-by-month realized income trends ordered oldest to newest.
 *
 * Requirements:
 * - Generates continuous data points (including zero-income months) for charting.
 * - Defaults to last 6 calendar months ending at [referenceMonth].
 * - Ordered oldest to newest.
 */
class GetIncomeTrendsUseCase @Inject constructor(
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase
) {

    operator fun invoke(
        incomes: List<Income>,
        monthCount: Int = 6,
        referenceMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo"))
    ): List<IncomeTrendPoint> {
        require(monthCount > 0) { "monthCount must be positive" }

        val startMonth = referenceMonth.minusMonths((monthCount - 1).toLong())
        val trendPoints = mutableListOf<IncomeTrendPoint>()

        var current = startMonth
        while (!current.isAfter(referenceMonth)) {
            val summary = getMonthlyIncomeSummaryUseCase(incomes, current)
            trendPoints.add(
                IncomeTrendPoint(
                    yearMonth = current.toString(),
                    amountMinor = summary.totalRealizedMinor
                )
            )
            current = current.plusMonths(1)
        }

        return trendPoints
    }
}
