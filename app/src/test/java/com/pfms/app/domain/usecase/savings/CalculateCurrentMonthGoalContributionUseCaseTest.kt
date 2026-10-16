package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class CalculateCurrentMonthGoalContributionUseCaseTest {

    private lateinit var useCase: CalculateCurrentMonthGoalContributionUseCase

    @Before
    fun setup() {
        useCase = CalculateCurrentMonthGoalContributionUseCase()
    }

    @Test
    fun `current month contributions are calculated correctly`() {
        val targetMonth = YearMonth.of(2026, 10)
        val transactions = listOf(
            GoalTransaction(id = "tx_1", goalId = "goal_1", amountMinor = 50_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05"),
            GoalTransaction(id = "tx_2", goalId = "goal_1", amountMinor = 30_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-18"),
            GoalTransaction(id = "tx_past", goalId = "goal_1", amountMinor = 100_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-09-25"),
            GoalTransaction(id = "tx_other_goal", goalId = "goal_2", amountMinor = 80_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-10")
        )

        val netContribution = useCase("goal_1", transactions, targetMonth)

        // 50,000 + 30,000 = 80,000
        assertEquals(80_000L, netContribution)
    }

    @Test
    fun `current month withdrawals reduce contribution pace`() {
        val targetMonth = YearMonth.of(2026, 10)
        val transactions = listOf(
            GoalTransaction(id = "tx_1", goalId = "goal_1", amountMinor = 70_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05"),
            GoalTransaction(id = "tx_2", goalId = "goal_1", amountMinor = 20_000L, transactionType = GoalTransactionType.WITHDRAWAL, date = "2026-10-12")
        )

        val netContribution = useCase("goal_1", transactions, targetMonth)

        // 70,000 - 20,000 = 50,000
        assertEquals(50_000L, netContribution)
    }

    @Test
    fun `net contribution can be negative if withdrawals exceed contributions in that month`() {
        val targetMonth = YearMonth.of(2026, 10)
        val transactions = listOf(
            GoalTransaction(id = "tx_1", goalId = "goal_1", amountMinor = 10_000L, transactionType = GoalTransactionType.CONTRIBUTION, date = "2026-10-05"),
            GoalTransaction(id = "tx_2", goalId = "goal_1", amountMinor = 40_000L, transactionType = GoalTransactionType.WITHDRAWAL, date = "2026-10-12")
        )

        val netContribution = useCase("goal_1", transactions, targetMonth)

        // 10,000 - 40,000 = -30,000
        assertEquals(-30_000L, netContribution)
    }
}
