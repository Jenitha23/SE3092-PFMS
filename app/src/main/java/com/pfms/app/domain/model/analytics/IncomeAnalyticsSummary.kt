package com.pfms.app.domain.model.analytics

/**
 * Aggregated analytics overview for the Income Dashboard.
 */
data class IncomeAnalyticsSummary(
    val selectedMonthSummary: MonthlyIncomeSummary?,
    val regularVsVariable: RegularVariableIncome,
    val trendPoints: List<IncomeTrendPoint>,
    val averageMonthlyIncomeMinor: Long,
    val highestIncomeMonth: String?,
    val highestIncomeMinor: Long,
    val estimationComparison: IncomeEstimationComparison? = null
)
