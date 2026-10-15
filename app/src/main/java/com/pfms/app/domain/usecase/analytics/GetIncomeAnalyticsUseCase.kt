package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.IncomeAnalyticsSummary
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Aggregates all analytics metrics for the income analytics dashboard.
 */
class GetIncomeAnalyticsUseCase @Inject constructor(
    private val getMonthlyIncomeSummaryUseCase: GetMonthlyIncomeSummaryUseCase,
    private val getRegularVsVariableIncomeUseCase: GetRegularVsVariableIncomeUseCase,
    private val getIncomeTrendsUseCase: GetIncomeTrendsUseCase,
    private val getIncomeEstimationComparisonUseCase: GetIncomeEstimationComparisonUseCase
) {

    operator fun invoke(
        incomes: List<Income>,
        targetMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo")),
        estimatedMonthlyIncomeMinor: Long? = null,
        trendMonthCount: Int = 6
    ): IncomeAnalyticsSummary {
        val selectedMonthSummary = getMonthlyIncomeSummaryUseCase(incomes, targetMonth)
        val regularVsVariable = getRegularVsVariableIncomeUseCase(selectedMonthSummary)
        val trendPoints = getIncomeTrendsUseCase(incomes, trendMonthCount, targetMonth)

        // Calculate average monthly income across the trend window
        val totalTrendAmount = trendPoints.sumOf { it.amountMinor }
        val averageMonthlyIncomeMinor = if (trendPoints.isNotEmpty()) {
            totalTrendAmount / trendPoints.size
        } else {
            0L
        }

        // Find highest income month across trends
        val highestPoint = trendPoints.maxByOrNull { it.amountMinor }
        val highestIncomeMonth = highestPoint?.takeIf { it.amountMinor > 0L }?.yearMonth
        val highestIncomeMinor = highestPoint?.amountMinor?.coerceAtLeast(0L) ?: 0L

        val estimationComparison = estimatedMonthlyIncomeMinor?.let { estimate ->
            getIncomeEstimationComparisonUseCase(
                estimatedMonthlyIncomeMinor = estimate,
                incomes = incomes,
                referenceMonth = targetMonth
            )
        }

        return IncomeAnalyticsSummary(
            selectedMonthSummary = selectedMonthSummary,
            regularVsVariable = regularVsVariable,
            trendPoints = trendPoints,
            averageMonthlyIncomeMinor = averageMonthlyIncomeMinor,
            highestIncomeMonth = highestIncomeMonth,
            highestIncomeMinor = highestIncomeMinor,
            estimationComparison = estimationComparison
        )
    }
}
