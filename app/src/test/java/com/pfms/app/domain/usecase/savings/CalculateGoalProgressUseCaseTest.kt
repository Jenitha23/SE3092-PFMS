package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class CalculateGoalProgressUseCaseTest {

    private lateinit var useCase: CalculateGoalProgressUseCase

    @Before
    fun setup() {
        useCase = CalculateGoalProgressUseCase(CalculateSavedAmountUseCase())
    }

    @Test
    fun `progress percentage is correct`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 250_000L,
            targetDate = "2027-10-16"
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        // 250,000 / 1,000,000 * 100 = 25.0%
        assertEquals(25.0, progress.progressPercentage, 0.001)
    }

    @Test
    fun `remaining amount is correct`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 300_000L,
            targetDate = "2027-10-16"
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        // 1,000,000 - 300,000 = 700,000
        assertEquals(700_000L, progress.remainingAmountMinor)
    }

    @Test
    fun `remaining months is correct for 12 months ahead`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_200_000L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16"
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        assertEquals(12L, progress.remainingMonths)
    }

    @Test
    fun `required monthly saving is correct`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_200_000L,
            openingSavedAmountMinor = 240_000L, // remaining: 960,000
            targetDate = "2027-06-16" // 8 months ahead from 2026-10-16
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        // remaining = 960,000 over 8 months = 120,000 per month
        assertEquals(8L, progress.remainingMonths)
        assertEquals(960_000L, progress.remainingAmountMinor)
        assertEquals(120_000L, progress.requiredMonthlySavingMinor)
    }

    @Test
    fun `zero or negative target amount is handled safely`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 0L,
            openingSavedAmountMinor = 0L,
            targetDate = "2027-10-16"
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        assertEquals(0.0, progress.progressPercentage, 0.001)
        assertEquals(0L, progress.remainingAmountMinor)
        assertEquals(0L, progress.requiredMonthlySavingMinor)
        assertFalse(progress.isCompleted)
    }

    @Test
    fun `completed goal shows exactly 100 percent progress and 0 remaining`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 500_000L,
            targetDate = "2027-10-16"
        )
        val transactions = listOf(
            GoalTransaction(
                id = "tx_1",
                goalId = "goal_1",
                amountMinor = 600_000L, // total saved = 1,100,000 >= 1,000,000
                transactionType = GoalTransactionType.CONTRIBUTION
            )
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, transactions, refDate)

        assertTrue(progress.isCompleted)
        assertEquals(100.0, progress.progressPercentage, 0.001)
        assertEquals(0L, progress.remainingAmountMinor)
        assertEquals(0L, progress.requiredMonthlySavingMinor)
        assertFalse(progress.isOverdue)
    }

    @Test
    fun `completed goal never shows negative remaining amount even with surplus`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 800_000L,
            targetDate = "2027-10-16"
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        assertTrue(progress.isCompleted)
        assertEquals(0L, progress.remainingAmountMinor)
        assertEquals(0L, progress.requiredMonthlySavingMinor)
    }

    @Test
    fun `overdue incomplete goal never produces negative months`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 200_000L,
            targetDate = "2026-08-01" // past date
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        assertTrue(progress.isOverdue)
        assertFalse(progress.isCompleted)
        assertEquals(0L, progress.remainingMonths)
    }

    @Test
    fun `overdue incomplete goal never divides by zero and provides safe required monthly saving`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 1_000_000L,
            openingSavedAmountMinor = 300_000L,
            targetDate = "2026-05-10" // past date
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        assertTrue(progress.isOverdue)
        assertEquals(0L, progress.remainingMonths)
        // Safe non-dividing required monthly saving = remaining amount
        assertEquals(700_000L, progress.remainingAmountMinor)
        assertEquals(700_000L, progress.requiredMonthlySavingMinor)
    }

    @Test
    fun `completed goal overrides overdue state`() {
        val goal = SavingsGoal(
            id = "goal_1",
            targetAmountMinor = 500_000L,
            openingSavedAmountMinor = 500_000L,
            targetDate = "2026-01-01" // target was in the past, but goal is fully saved
        )
        val refDate = LocalDate.of(2026, 10, 16)

        val progress = useCase(goal, emptyList(), refDate)

        assertTrue(progress.isCompleted)
        // Per FR-28 rule #32: completed goal overrides overdue state
        assertFalse(progress.isOverdue)
        assertEquals(100.0, progress.progressPercentage, 0.001)
    }
}
