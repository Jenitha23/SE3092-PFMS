package com.pfms.app.domain.model.analytics

enum class EstimationStatus {
    ABOVE_ESTIMATE,
    BELOW_ESTIMATE,
    ON_TARGET
}

/**
 * Compares user-entered estimated monthly income against the actual
 * trailing 3-calendar-month average income.
 *
 * @property estimatedMonthlyIncomeMinor The expected monthly income in minor units.
 * @property trailingThreeMonthAverageMinor The calculated actual average over 3 calendar months.
 * @property differenceMinor actualAverage - estimated (positive if above estimate, negative if below).
 * @property variancePercentage Percentage difference relative to estimate: ((actual - estimated) / estimated) * 100.
 * @property status Categorization of the comparison (ABOVE_ESTIMATE, BELOW_ESTIMATE, ON_TARGET).
 * @property monthsEvaluated The list of year-month keys (e.g. ["2026-07", "2026-08", "2026-09"]) used in the average.
 */
data class IncomeEstimationComparison(
    val estimatedMonthlyIncomeMinor: Long,
    val trailingThreeMonthAverageMinor: Long,
    val differenceMinor: Long,
    val variancePercentage: Double,
    val status: EstimationStatus,
    val monthsEvaluated: List<String> = emptyList()
)
