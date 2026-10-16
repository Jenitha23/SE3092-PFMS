package com.pfms.app.domain.model.savings

/**
 * Calculated progress snapshot for a savings goal (FR-25, FR-28).
 *
 * Calculated fields are kept distinct from the persistent [com.pfms.app.domain.model.SavingsGoal] model.
 */
data class SavingsGoalProgress(
    val currentSavedMinor: Long,
    val progressPercentage: Double,
    val remainingAmountMinor: Long,
    val remainingMonths: Long,
    val requiredMonthlySavingMinor: Long,
    val isCompleted: Boolean,
    val isOverdue: Boolean
)
