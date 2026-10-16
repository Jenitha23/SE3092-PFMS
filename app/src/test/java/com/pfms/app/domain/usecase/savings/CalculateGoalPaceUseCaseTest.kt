package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.domain.model.savings.PaceBenchmark
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CalculateGoalPaceUseCaseTest {

    private lateinit var useCase: CalculateGoalPaceUseCase

    @Before
    fun setup() {
        val savedAmountUseCase = CalculateSavedAmountUseCase()
        val goalProgressUseCase = CalculateGoalProgressUseCase(savedAmountUseCase)
        val currentMonthGoalContributionUseCase = CalculateCurrentMonthGoalContributionUseCase()
        val monthlyNetSavingsUseCase = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
        val trailingNetSavingsAverageUseCase = CalculateTrailingNetSavingsAverageUseCase(monthlyNetSavingsUseCase)

        useCase = CalculateGoalPaceUseCase(
            calculateGoalProgressUseCase = goalProgressUseCase,
            calculateCurrentMonthGoalContributionUseCase = currentMonthGoalContributionUseCase,
            calculateMonthlyNetSavingsUseCase = monthlyNetSavingsUseCase,
            calculateTrailingNetSavingsAverageUseCase = trailingNetSavingsAverageUseCase
        )
    }

    @Test
    fun `on track classification and zero shortfall when pace exceeds required monthly saving`() {
        val refDate = LocalDate.of(2026, 10, 16)
        // Goal: 1,200,000 target, 0 opening, 12 months ahead -> required = 100,000 / month
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_200_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16"
        )
        // Trailing income: 150,000 / month in M-3, M-2, M-1 -> trailing avg = 150,000 >= 100,000
        val incomes = listOf(
            Income(id = "i1", amountMinor = 150_000L, source = "Salary", receivedDate = "2026-07-10"),
            Income(id = "i2", amountMinor = 150_000L, source = "Salary", receivedDate = "2026-08-10"),
            Income(id = "i3", amountMinor = 150_000L, source = "Salary", receivedDate = "2026-09-10")
        )

        val pace = useCase(
            goal = goal,
            transactions = emptyList(),
            incomes = incomes,
            expenses = emptyList(),
            referenceDate = refDate,
            benchmark = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
        )

        assertEquals(100_000L, pace.requiredMonthlySavingMinor)
        assertEquals(150_000L, pace.trailingThreeMonthAverageMinor)
        assertEquals(GoalPaceStatus.ON_TRACK, pace.paceStatus)
        assertEquals(0L, pace.shortfallMinor)
    }

    @Test
    fun `behind classification and positive shortfall when pace is lower than required monthly saving`() {
        val refDate = LocalDate.of(2026, 10, 16)
        // Goal: 1,200,000 target, 0 opening, 12 months ahead -> required = 100,000 / month
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_200_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16"
        )
        // Trailing income: 60,000 / month -> trailing avg = 60,000 < 100,000
        val incomes = listOf(
            Income(id = "i1", amountMinor = 60_000L, source = "Salary", receivedDate = "2026-07-10"),
            Income(id = "i2", amountMinor = 60_000L, source = "Salary", receivedDate = "2026-08-10"),
            Income(id = "i3", amountMinor = 60_000L, source = "Salary", receivedDate = "2026-09-10")
        )

        val pace = useCase(
            goal = goal,
            transactions = emptyList(),
            incomes = incomes,
            expenses = emptyList(),
            referenceDate = refDate,
            benchmark = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
        )

        assertEquals(100_000L, pace.requiredMonthlySavingMinor)
        assertEquals(60_000L, pace.trailingThreeMonthAverageMinor)
        assertEquals(GoalPaceStatus.BEHIND, pace.paceStatus)
        // shortfall = 100,000 - 60,000 = 40,000
        assertEquals(40_000L, pace.shortfallMinor)
    }

    @Test
    fun `projected completion date is calculated from pace and remaining amount`() {
        val refDate = LocalDate.of(2026, 10, 16)
        // Goal: 600,000 remaining
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 600_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16"
        )
        // Trailing avg = 100,000 / month
        val incomes = listOf(
            Income(id = "i1", amountMinor = 100_000L, source = "Salary", receivedDate = "2026-07-10"),
            Income(id = "i2", amountMinor = 100_000L, source = "Salary", receivedDate = "2026-08-10"),
            Income(id = "i3", amountMinor = 100_000L, source = "Salary", receivedDate = "2026-09-10")
        )

        val pace = useCase(
            goal = goal,
            transactions = emptyList(),
            incomes = incomes,
            expenses = emptyList(),
            referenceDate = refDate,
            benchmark = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
        )

        // remaining = 600,000, pace = 100,000 -> 6 months needed
        // 2026-10-16 + 6 months = 2027-04-16
        assertEquals(LocalDate.of(2027, 4, 16), pace.projectedCompletionDate)
    }

    @Test
    fun `non-positive pace returns null projected completion date rather than invalid date`() {
        val refDate = LocalDate.of(2026, 10, 16)
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16"
        )

        // No income -> pace = 0L
        val pace = useCase(
            goal = goal,
            transactions = emptyList(),
            incomes = emptyList(),
            expenses = emptyList(),
            referenceDate = refDate,
            benchmark = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
        )

        assertEquals(GoalPaceStatus.BEHIND, pace.paceStatus)
        assertEquals(pace.requiredMonthlySavingMinor, pace.shortfallMinor)
        // Cannot project completion date when pace <= 0
        assertNull(pace.projectedCompletionDate)
    }

    @Test
    fun `pace evaluation supports current month contributions as chosen benchmark`() {
        val refDate = LocalDate.of(2026, 10, 16)
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_200_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16" // required = 100,000
        )
        val transactions = listOf(
            GoalTransaction(id = "tx1", goalId = "goal_1", amountMinor = 120_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05")
        )

        val pace = useCase(
            goal = goal,
            transactions = transactions,
            incomes = emptyList(),
            expenses = emptyList(),
            referenceDate = refDate,
            benchmark = PaceBenchmark.CURRENT_MONTH_CONTRIBUTIONS
        )

        assertEquals(120_000L, pace.currentMonthContributionMinor)
        assertEquals(GoalPaceStatus.ON_TRACK, pace.paceStatus)
        assertEquals(0L, pace.shortfallMinor)
    }
}
