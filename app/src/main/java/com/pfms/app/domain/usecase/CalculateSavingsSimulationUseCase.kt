package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.SavingsSimulation
import com.pfms.app.domain.usecase.savings.CalculateSavedAmountUseCase
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Read-only what-if calculation. No money is transferred or contributed. */
class CalculateSavingsSimulationUseCase @Inject constructor(
    private val savedAmount: CalculateSavedAmountUseCase
) {
    fun simulate(
        goal: SavingsGoal,
        transactions: List<GoalTransaction>,
        monthlyReductionMinor: Long,
        plannedMonthlyContributionMinor: Long = 0L,
        asOfDate: LocalDate
    ): SavingsSimulation {
        require(monthlyReductionMinor >= 0L) { "Spending reduction must not be negative" }
        require(plannedMonthlyContributionMinor >= 0L) { "Planned contribution must not be negative" }
        require(goal.targetAmountMinor > 0L) { "Savings target must be positive" }
        val deadline = LocalDate.parse(goal.targetDate)
        val current = savedAmount(goal, transactions)
        val finished = current >= goal.targetAmountMinor
        val overdue = !finished && asOfDate.isAfter(deadline)
        val months = if (overdue) 0L else maxOf(1L, ChronoUnit.MONTHS.between(YearMonth.from(asOfDate), YearMonth.from(deadline)))
        val extraMonthly = BigInteger.valueOf(monthlyReductionMinor)
        val totalMonthly = extraMonthly + BigInteger.valueOf(plannedMonthlyContributionMinor)
        val extra = if (finished || overdue) BigInteger.ZERO else extraMonthly * BigInteger.valueOf(months)
        val projected = BigInteger.valueOf(current) + if (finished || overdue) BigInteger.ZERO else totalMonthly * BigInteger.valueOf(months)
        val target = BigInteger.valueOf(goal.targetAmountMinor)
        val percent = (projected * BigInteger.valueOf(100L) / target)
            .coerceAtMost(BigInteger.valueOf(100L)).toInt()
        val shortfall = (target - projected).coerceAtLeast(BigInteger.ZERO)
        val completion = when {
            finished -> asOfDate
            totalMonthly == BigInteger.ZERO -> null
            else -> {
                val stillNeeded = (target - BigInteger.valueOf(current)).coerceAtLeast(BigInteger.ZERO)
                val monthsNeeded = (stillNeeded + totalMonthly - BigInteger.ONE) / totalMonthly
                if (monthsNeeded > BigInteger.valueOf(1200L)) null
                else asOfDate.plusMonths(monthsNeeded.toLong())
            }
        }
        return SavingsSimulation(
            asOfDate = asOfDate,
            monthsUntilDeadline = months,
            currentSavedMinor = current,
            targetMinor = goal.targetAmountMinor,
            monthlyReductionMinor = monthlyReductionMinor,
            monthlyPlannedContributionMinor = plannedMonthlyContributionMinor,
            potentialExtraByDeadlineMinor = extra.longValueExact(),
            projectedBalanceAtDeadlineMinor = projected.longValueExact(),
            projectedProgressPercent = percent,
            projectedShortfallMinor = shortfall.longValueExact(),
            estimatedCompletionDate = completion,
            isGoalAlreadyComplete = finished,
            isGoalOverdue = overdue
        )
    }
}
