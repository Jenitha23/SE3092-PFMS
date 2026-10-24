package com.pfms.app.domain.model

import java.time.LocalDate

data class SavingsSimulation(
    val asOfDate: LocalDate,
    val monthsUntilDeadline: Long,
    val currentSavedMinor: Long,
    val targetMinor: Long,
    val monthlyReductionMinor: Long,
    val monthlyPlannedContributionMinor: Long,
    val potentialExtraByDeadlineMinor: Long,
    val projectedBalanceAtDeadlineMinor: Long,
    val projectedProgressPercent: Int,
    val projectedShortfallMinor: Long,
    val estimatedCompletionDate: LocalDate?,
    val isGoalAlreadyComplete: Boolean,
    val isGoalOverdue: Boolean
)
