package com.pfms.app.domain.model.savings

import java.time.LocalDate

/**
 * Calculated pace and projected completion snapshot for a savings goal (FR-26).
 *
 * Compares required monthly saving against:
 * 1. Current month goal contributions (actual ledger allocations)
 * 2. Current month net savings (income - expenses)
 * 3. Trailing 3-month average net savings (sustainable baseline)
 */
data class SavingsGoalPace(
    val requiredMonthlySavingMinor: Long,
    val currentMonthContributionMinor: Long,
    val currentMonthNetSavingsMinor: Long,
    val trailingThreeMonthAverageMinor: Long,
    val paceStatus: GoalPaceStatus,
    val shortfallMinor: Long,
    val projectedCompletionDate: LocalDate?,
    val benchmarkUsed: PaceBenchmark = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
)

/**
 * Pace classification for a savings goal.
 */
enum class GoalPaceStatus {
    ON_TRACK,
    BEHIND
}

/**
 * Benchmark strategy used to evaluate goal pace.
 */
enum class PaceBenchmark {
    TRAILING_THREE_MONTH_AVERAGE,
    CURRENT_MONTH_NET_SAVINGS,
    CURRENT_MONTH_CONTRIBUTIONS
}
