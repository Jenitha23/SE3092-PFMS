package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateSavedAmountUseCaseTest {

    private lateinit var useCase: CalculateSavedAmountUseCase

    @Before
    fun setup() {
        useCase = CalculateSavedAmountUseCase()
    }

    @Test
    fun `opening amount only when ledger is empty`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 1_000_000L
        )

        val saved = useCase(goal, emptyList())

        assertEquals(1_000_000L, saved)
    }

    @Test
    fun `contributions increase saved amount`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 1_000_000L
        )
        val transactions = listOf(
            GoalTransaction(
                id = "tx_1",
                goalId = "goal_1",
                userId = "user_1",
                amountMinor = 500_000L,
                transactionType = GoalTransactionType.CONTRIBUTION
            ),
            GoalTransaction(
                id = "tx_2",
                goalId = "goal_1",
                userId = "user_1",
                amountMinor = 250_000L,
                transactionType = GoalTransactionType.CONTRIBUTION
            )
        )

        val saved = useCase(goal, transactions)

        // 1,000,000 + 500,000 + 250,000 = 1,750,000
        assertEquals(1_750_000L, saved)
    }

    @Test
    fun `withdrawals reduce saved amount`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 1_000_000L
        )
        val transactions = listOf(
            GoalTransaction(
                id = "tx_1",
                goalId = "goal_1",
                userId = "user_1",
                amountMinor = 300_000L,
                transactionType = GoalTransactionType.WITHDRAWAL
            )
        )

        val saved = useCase(goal, transactions)

        // 1,000,000 - 300,000 = 700,000
        assertEquals(700_000L, saved)
    }

    @Test
    fun `mixed ledger calculates correctly`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 1_000_000L
        )
        val transactions = listOf(
            GoalTransaction(id = "tx_1", goalId = "goal_1", userId = "user_1", amountMinor = 500_000L, transactionType = GoalTransactionType.CONTRIBUTION),
            GoalTransaction(id = "tx_2", goalId = "goal_1", userId = "user_1", amountMinor = 200_000L, transactionType = GoalTransactionType.WITHDRAWAL),
            GoalTransaction(id = "tx_3", goalId = "goal_1", userId = "user_1", amountMinor = 100_000L, transactionType = GoalTransactionType.CONTRIBUTION),
            GoalTransaction(id = "tx_4", goalId = "goal_1", userId = "user_1", amountMinor = 50_000L, transactionType = GoalTransactionType.WITHDRAWAL)
        )

        val saved = useCase(goal, transactions)

        // 1,000,000 + 500,000 - 200,000 + 100,000 - 50,000 = 1,350,000
        assertEquals(1_350_000L, saved)
    }

    @Test
    fun `other goal transactions are ignored`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 1_000_000L
        )
        val transactions = listOf(
            GoalTransaction(id = "tx_1", goalId = "goal_1", userId = "user_1", amountMinor = 200_000L, transactionType = GoalTransactionType.CONTRIBUTION),
            GoalTransaction(id = "tx_other", goalId = "goal_2", userId = "user_1", amountMinor = 999_999L, transactionType = GoalTransactionType.CONTRIBUTION),
            GoalTransaction(id = "tx_other_user", goalId = "goal_1", userId = "user_2", amountMinor = 500_000L, transactionType = GoalTransactionType.WITHDRAWAL)
        )

        val saved = useCase(goal, transactions)

        // Only tx_1 counts: 1,000,000 + 200,000 = 1,200,000
        assertEquals(1_200_000L, saved)
    }

    @Test
    fun `monthly surplus is never automatically added`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 500_000L
        )

        // No ledger transactions added: even if user has large income/surplus elsewhere,
        // FR-24 dictates that only explicit ledger transactions contribute.
        val saved = useCase(goal, emptyList())

        assertEquals(500_000L, saved)
    }

    @Test
    fun `excessive withdrawals clamp safely to zero`() {
        val goal = SavingsGoal(
            id = "goal_1",
            userId = "user_1",
            openingSavedAmountMinor = 100_000L
        )
        val transactions = listOf(
            GoalTransaction(
                id = "tx_1",
                goalId = "goal_1",
                userId = "user_1",
                amountMinor = 300_000L,
                transactionType = GoalTransactionType.WITHDRAWAL
            )
        )

        val saved = useCase(goal, transactions)

        // 100,000 - 300,000 = -200,000 -> clamped to 0L
        assertEquals(0L, saved)
    }
}
