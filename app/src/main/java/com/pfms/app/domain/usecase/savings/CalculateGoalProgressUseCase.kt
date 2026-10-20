package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.savings.SavingsGoalProgress
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Pure domain use case implementing FR-25 and FR-28:
 * Calculates progress %, remaining amount, calendar remaining months, required monthly saving,
 * and completed/overdue status.
 *
 * Remaining Months Specification:
 * - Uses calendar-month calculation via [YearMonth] and [ChronoUnit.MONTHS].
 * - Avoids day/30 approximations.
 * - If referenceDate is after targetDate: remainingMonths = 0.
 * - If referenceDate is in the same or earlier calendar month than targetDate:
 *   remainingMonths = max(1L, ChronoUnit.MONTHS.between(YearMonth.from(referenceDate), YearMonth.from(targetDate))).
 *
 * Rules:
 * - Completed: currentSaved >= targetAmount (with targetAmount > 0).
 *   progress = 100%, remainingAmount = 0L, requiredMonthlySaving = 0L. Completed state overrides overdue.
 * - Overdue: referenceDate > targetDate when goal is incomplete.
 *   isOverdue = true, remainingMonths = 0L. requiredMonthlySaving is safely set to remainingAmountMinor
 *   without division-by-zero.
 * - Progress: strictly clamped to [0.0, 100.0].
 * - Defensive: handles 0 or negative target amounts gracefully without division by zero.
 */
class CalculateGoalProgressUseCase @Inject constructor(
    private val calculateSavedAmountUseCase: CalculateSavedAmountUseCase
) {

    operator fun invoke(
        goal: SavingsGoal,
        transactions: List<GoalTransaction>,
        referenceDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Colombo"))
    ): SavingsGoalProgress {
        val currentSaved = calculateSavedAmountUseCase(goal, transactions)
        val targetAmount = maxOf(0L, goal.targetAmountMinor)

        // Parse target date defensively
        val targetDate = try {
            if (goal.targetDate.isNotBlank()) LocalDate.parse(goal.targetDate) else null
        } catch (e: DateTimeParseException) {
            null
        }

        // 1. Check Completed State (FR-25, FR-28)
        if (targetAmount > 0L && currentSaved >= targetAmount) {
            return SavingsGoalProgress(
                currentSavedMinor = currentSaved,
                progressPercentage = 100.0,
                remainingAmountMinor = 0L,
                remainingMonths = calculateRemainingMonths(referenceDate, targetDate),
                requiredMonthlySavingMinor = 0L,
                isCompleted = true,
                isOverdue = false // Completed overrides overdue (FR-28)
            )
        }

        // 2. Check Overdue State (FR-28)
        val isOverdue = targetDate != null && referenceDate.isAfter(targetDate)
        val remainingAmount = maxOf(0L, targetAmount - currentSaved)

        val progressPercentage = if (targetAmount <= 0L) {
            0.0
        } else {
            SafeCalculations.percentage(currentSaved, targetAmount).coerceIn(0.0, 100.0)
        }

        val remainingMonths = if (isOverdue || targetDate == null) {
            0L
        } else {
            calculateRemainingMonths(referenceDate, targetDate)
        }

        val requiredMonthlySaving = when {
            remainingAmount == 0L -> 0L
            remainingMonths <= 0L -> remainingAmount // Overdue safe fallback: no divide-by-zero
            else -> remainingAmount / remainingMonths + if (remainingAmount % remainingMonths == 0L) 0L else 1L
        }

        return SavingsGoalProgress(
            currentSavedMinor = currentSaved,
            progressPercentage = progressPercentage,
            remainingAmountMinor = remainingAmount,
            remainingMonths = remainingMonths,
            requiredMonthlySavingMinor = requiredMonthlySaving,
            isCompleted = false,
            isOverdue = isOverdue
        )
    }

    private fun calculateRemainingMonths(referenceDate: LocalDate, targetDate: LocalDate?): Long {
        if (targetDate == null || referenceDate.isAfter(targetDate)) {
            return 0L
        }
        val refMonth = YearMonth.from(referenceDate)
        val tgtMonth = YearMonth.from(targetDate)
        val monthsBetween = ChronoUnit.MONTHS.between(refMonth, tgtMonth)
        // If within the same month and not overdue, at least 1 month is available to save
        return maxOf(1L, monthsBetween)
    }
}
