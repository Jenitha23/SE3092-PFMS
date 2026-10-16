package com.pfms.app.domain.model.savings

/**
 * Calculated impact of discretionary spending relative to a savings goal (FR-27).
 * Expresses current month discretionary spending as a percentage of one month's required saving.
 */
data class DiscretionarySpendingImpact(
    val discretionaryExpensesMinor: Long,
    val committedExpensesMinor: Long,
    val totalExpensesMinor: Long,
    val requiredMonthlySavingMinor: Long,
    val percentageOfRequiredSaving: Double
)
