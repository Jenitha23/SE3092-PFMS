package com.pfms.app.viewmodel

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.savings.DiscretionarySpendingImpact
import com.pfms.app.domain.model.savings.SavingsGoalPace

/**
 * UI state representation for the Savings Goal feature (FR-23 through FR-28).
 *
 * Exposes active savings goal details, ledger transactions, progress metrics,
 * pace analytics, discretionary spending impact, and operation status.
 */
data class SavingsGoalUiState(
    val isLoading: Boolean = true,
    val activeGoal: SavingsGoal? = null,
    val transactions: List<GoalTransaction> = emptyList(),

    val currentSavedMinor: Long = 0L,
    val remainingAmountMinor: Long = 0L,
    val progressPercent: Double = 0.0,
    val remainingMonths: Long = 0L,
    val requiredMonthlySavingMinor: Long = 0L,

    val currentMonthContributionMinor: Long = 0L,
    val currentMonthNetSavingsMinor: Long = 0L,
    val trailingThreeMonthNetSavingsMinor: Long = 0L,

    val pace: SavingsGoalPace? = null,
    val discretionaryImpact: DiscretionarySpendingImpact? = null,

    val isCompleted: Boolean = false,
    val isOverdue: Boolean = false,

    val errorMessage: String? = null,
    val operationInProgress: Boolean = false,
    val canRetry: Boolean = false,
    val successVersion: Long = 0L,
    /** Makes repeated identical rejections observable even when loading emissions are conflated. */
    val failureVersion: Long = 0L
)
