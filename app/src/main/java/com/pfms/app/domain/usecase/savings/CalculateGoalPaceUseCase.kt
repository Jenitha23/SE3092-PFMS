package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.domain.model.savings.PaceBenchmark
import com.pfms.app.domain.model.savings.SavingsGoalPace
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.ceil

/**
 * Pure calculation use case implementing FR-26:
 * Evaluates savings goal pace against benchmarks, classifies status as ON_TRACK or BEHIND,
 * calculates shortfall, and projects completion date.
 *
 * Benchmark Strategy:
 * - Exposes all 3 comparison values:
 *   1. [currentMonthContributionMinor]: goal ledger allocations this month
 *   2. [currentMonthNetSavingsMinor]: general net savings (income - expenses) this month
 *   3. [trailingThreeMonthAverageMinor]: 3-month sustainable net savings average
 * - Primary classification defaults to [PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE] because
 *   a 3-month trailing average smooths out lumpy single-month anomalies and represents
 *   a sustainable savings capacity.
 *
 * Shortfall:
 * - shortfall = max(0, requiredMonthlySaving - chosenPace)
 *
 * Projected Completion:
 * - If remainingAmount == 0: referenceDate
 * - If chosenPace <= 0: null (indefinite / impossible to project)
 * - If chosenPace > 0: referenceDate + ceil(remainingAmount / chosenPace) months
 */
class CalculateGoalPaceUseCase @Inject constructor(
    private val calculateGoalProgressUseCase: CalculateGoalProgressUseCase,
    private val calculateCurrentMonthGoalContributionUseCase: CalculateCurrentMonthGoalContributionUseCase,
    private val calculateMonthlyNetSavingsUseCase: CalculateMonthlyNetSavingsUseCase,
    private val calculateTrailingNetSavingsAverageUseCase: CalculateTrailingNetSavingsAverageUseCase
) {

    operator fun invoke(
        goal: SavingsGoal,
        transactions: List<GoalTransaction>,
        incomes: List<Income>,
        expenses: List<Expense>,
        referenceDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Colombo")),
        benchmark: PaceBenchmark = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
    ): SavingsGoalPace {
        val progress = calculateGoalProgressUseCase(goal, transactions, referenceDate)
        val refMonth = YearMonth.from(referenceDate)

        val currentMonthContribution = calculateCurrentMonthGoalContributionUseCase(
            goalId = goal.id,
            transactions = transactions,
            targetMonth = refMonth
        )

        val currentMonthNetSavings = calculateMonthlyNetSavingsUseCase(
            incomes = incomes,
            expenses = expenses,
            targetMonth = refMonth
        )

        val trailingAvgResult = calculateTrailingNetSavingsAverageUseCase(
            incomes = incomes,
            expenses = expenses,
            referenceMonth = refMonth
        )
        val trailingAvg = trailingAvgResult.averageMinor

        val paceValue = when (benchmark) {
            PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE -> trailingAvg
            PaceBenchmark.CURRENT_MONTH_NET_SAVINGS -> currentMonthNetSavings
            PaceBenchmark.CURRENT_MONTH_CONTRIBUTIONS -> currentMonthContribution
        }

        val requiredSaving = progress.requiredMonthlySavingMinor
        val remainingAmount = progress.remainingAmountMinor

        val (paceStatus, shortfall) = when {
            progress.isCompleted || requiredSaving <= 0L -> {
                GoalPaceStatus.ON_TRACK to 0L
            }
            paceValue >= requiredSaving -> {
                GoalPaceStatus.ON_TRACK to 0L
            }
            else -> {
                val diff = maxOf(0L, requiredSaving - paceValue)
                GoalPaceStatus.BEHIND to diff
            }
        }

        val projectedCompletionDate = when {
            remainingAmount <= 0L -> referenceDate
            paceValue <= 0L -> null // Non-positive pace cannot project a valid completion date
            else -> {
                val monthsNeeded = ceil(remainingAmount.toDouble() / paceValue.toDouble()).toLong()
                referenceDate.plusMonths(monthsNeeded)
            }
        }

        return SavingsGoalPace(
            requiredMonthlySavingMinor = requiredSaving,
            currentMonthContributionMinor = currentMonthContribution,
            currentMonthNetSavingsMinor = currentMonthNetSavings,
            trailingThreeMonthAverageMinor = trailingAvg,
            paceStatus = paceStatus,
            shortfallMinor = shortfall,
            projectedCompletionDate = projectedCompletionDate,
            benchmarkUsed = benchmark
        )
    }
}
