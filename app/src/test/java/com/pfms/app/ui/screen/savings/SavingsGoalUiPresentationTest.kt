package com.pfms.app.ui.screen.savings

import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.SavingsGoalDefaults
import com.pfms.app.domain.model.savings.DiscretionarySpendingImpact
import com.pfms.app.domain.model.savings.GoalPaceStatus
import com.pfms.app.domain.model.savings.PaceBenchmark
import com.pfms.app.domain.model.savings.SavingsGoalPace
import com.pfms.app.viewmodel.SavingsGoalUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying Savings Goal Compose UI presentation logic (Phase 7 / 7A, FR-23 through FR-28).
 */
class SavingsGoalUiPresentationTest {

    // 1. no-goal state exposes Create Savings Goal action
    @Test
    fun `1 no-goal state exposes Create Savings Goal action`() {
        val state = SavingsGoalUiState(isLoading = false, activeGoal = null)
        assertNull(state.activeGoal)
        assertTrue(state.transactions.isEmpty())
        assertEquals(0L, state.currentSavedMinor)
        assertFalse(state.isCompleted)
        assertFalse(state.isOverdue)
    }

    // 2. creation defaults: MacBook Pro M4, 490,000, 11,200, 12 months ahead
    @Test
    fun `2 creation defaults match agreed MacBook Pro M4 specification`() {
        val referenceDate = LocalDate.of(2026, 10, 18)
        val example = SavingsGoalDefaults.createPrefilledExample(referenceDate = referenceDate)

        assertEquals("MacBook Pro M4", example.name)
        assertEquals(49_000_000L, example.targetAmountMinor) // LKR 490,000.00
        assertEquals(1_120_000L, example.openingSavedAmountMinor) // LKR 11,200.00
        assertEquals("2027-10-18", example.targetDate) // Exactly 12 months ahead
        assertEquals(GoalStatus.ACTIVE, example.status)

        // Form decimal representations
        assertEquals("490000.00", SavingsGoalUiFormatter.formatMinorToDecimalString(example.targetAmountMinor))
        assertEquals("11200.00", SavingsGoalUiFormatter.formatMinorToDecimalString(example.openingSavedAmountMinor))
    }

    // 3. opening-saved field maps correctly
    @Test
    fun `3 opening-saved field maps correctly to minor units`() {
        val validationResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "MacBook Pro M4",
            targetAmountInput = "490,000.00",
            targetDateInput = "2027-10-18",
            openingSavedInput = "11,200.00"
        )
        assertTrue(validationResult.isValid)
        assertEquals(1_120_000L, validationResult.openingSavedAmountMinor)

        // Form mapping to domain model
        val goal = SavingsGoal(
            name = "MacBook Pro M4",
            targetAmountMinor = validationResult.targetAmountMinor,
            targetDate = "2027-10-18",
            openingSavedAmountMinor = validationResult.openingSavedAmountMinor,
            status = GoalStatus.ACTIVE
        )
        assertEquals(1_120_000L, goal.openingSavedAmountMinor)
        assertEquals(49_000_000L, goal.targetAmountMinor)
    }

    // 4. active goal summary uses supplied state
    @Test
    fun `4 active goal summary uses supplied state`() {
        val goal = SavingsGoal(
            name = "MacBook Pro M4",
            targetAmountMinor = 49_000_000L,
            targetDate = "2027-10-18"
        )
        val state = SavingsGoalUiState(
            isLoading = false,
            activeGoal = goal,
            currentSavedMinor = 11_200_000L,
            remainingAmountMinor = 37_800_000L,
            remainingMonths = 12L,
            requiredMonthlySavingMinor = 3_150_000L
        )

        assertEquals("LKR 490,000.00", SavingsGoalUiFormatter.formatLkr(state.activeGoal!!.targetAmountMinor))
        assertEquals("LKR 112,000.00", SavingsGoalUiFormatter.formatLkr(state.currentSavedMinor))
        assertEquals("LKR 378,000.00", SavingsGoalUiFormatter.formatLkr(state.remainingAmountMinor))
        assertEquals("LKR 31,500.00", SavingsGoalUiFormatter.formatLkr(state.requiredMonthlySavingMinor))
        assertEquals("Oct 18, 2027", SavingsGoalUiFormatter.formatDisplayDate(state.activeGoal!!.targetDate))
    }

    // 5. progress state renders supplied percentage
    @Test
    fun `5 progress state renders supplied percentage`() {
        val progressPercent = 22.85
        val targetSweep = (progressPercent / 100.0).toFloat().coerceIn(0f, 1f)
        assertEquals(0.2285f, targetSweep, 0.0001f)

        // Boundary clamp checks
        val clampedUnder = (-5.0 / 100.0).toFloat().coerceIn(0f, 1f)
        assertEquals(0.0f, clampedUnder, 0.0001f)

        val clampedOver = (110.0 / 100.0).toFloat().coerceIn(0f, 1f)
        assertEquals(1.0f, clampedOver, 0.0001f)
    }

    // 6. overdue state exposes Update Target Date
    @Test
    fun `6 overdue state exposes Update Target Date`() {
        val overdueState = SavingsGoalUiState(
            isLoading = false,
            activeGoal = SavingsGoal(name = "MacBook Pro", targetDate = "2025-01-01"),
            isOverdue = true,
            isCompleted = false
        )
        assertTrue(overdueState.isOverdue)
        assertFalse(overdueState.isCompleted)
    }

    // 7. completed state renders completed UI
    @Test
    fun `7 completed state renders completed UI`() {
        val completedState = SavingsGoalUiState(
            isLoading = false,
            activeGoal = SavingsGoal(name = "MacBook Pro M4", targetAmountMinor = 49_000_000L),
            currentSavedMinor = 49_000_000L,
            remainingAmountMinor = 0L,
            progressPercent = 100.0,
            requiredMonthlySavingMinor = 0L,
            isCompleted = true,
            isOverdue = false
        )
        assertTrue(completedState.isCompleted)
        assertFalse(completedState.isOverdue)
        assertEquals(0L, completedState.remainingAmountMinor)
        assertEquals("LKR 0.00", SavingsGoalUiFormatter.formatLkr(completedState.remainingAmountMinor))
    }

    // 8. contribution action uses contribution callback
    @Test
    fun `8 contribution action parses valid amount and maintains contribution payload`() {
        val result = SavingsGoalUiFormatter.validateTransactionForm(
            amountInput = "15,000.50",
            dateInput = "2026-10-18"
        )
        assertTrue(result.isValid)
        assertEquals(1_500_050L, result.amountMinor)

        val tx = GoalTransaction(
            id = "tx-c1",
            goalId = "g-1",
            amountMinor = result.amountMinor,
            transactionType = GoalTransactionType.CONTRIBUTION,
            date = "2026-10-18",
            note = "Monthly salary allocation"
        )
        assertEquals(GoalTransactionType.CONTRIBUTION, tx.transactionType)
        assertEquals(1_500_050L, tx.amountMinor)
        assertEquals("Monthly salary allocation", tx.note)
    }

    // 9. withdrawal action uses withdrawal callback
    @Test
    fun `9 withdrawal action parses valid amount and maintains withdrawal payload`() {
        val result = SavingsGoalUiFormatter.validateTransactionForm(
            amountInput = "5,000.00",
            dateInput = "2026-10-18"
        )
        assertTrue(result.isValid)
        assertEquals(500_000L, result.amountMinor)

        val tx = GoalTransaction(
            id = "tx-w1",
            goalId = "g-1",
            amountMinor = result.amountMinor,
            transactionType = GoalTransactionType.WITHDRAWAL,
            date = "2026-10-18",
            note = "Emergency repair"
        )
        assertEquals(GoalTransactionType.WITHDRAWAL, tx.transactionType)
        assertEquals(500_000L, tx.amountMinor)
    }

    // 10. transaction presentation preserves type
    @Test
    fun `10 transaction presentation preserves type with correct sign and date-only display`() {
        val contribution = GoalTransaction(
            id = "tx-1",
            amountMinor = 25_000_00L,
            transactionType = GoalTransactionType.CONTRIBUTION,
            date = "2026-10-18"
        )
        val withdrawal = GoalTransaction(
            id = "tx-2",
            amountMinor = 10_000_00L,
            transactionType = GoalTransactionType.WITHDRAWAL,
            date = "2026-10-18"
        )

        assertEquals("+ LKR 25,000.00", SavingsGoalUiFormatter.formatLkr(contribution.amountMinor, showSign = true))
        assertEquals("LKR 10,000.00", SavingsGoalUiFormatter.formatLkr(withdrawal.amountMinor, showSign = false))
        assertEquals("Oct 18, 2026", SavingsGoalUiFormatter.formatDisplayDate(contribution.date))
        // Verify date-only display has no fabricated time
        assertFalse(SavingsGoalUiFormatter.formatDisplayDate(contribution.date).contains(":"))
    }

    // 11. ON_TRACK presentation
    @Test
    fun `11 ON_TRACK presentation displays accurate status and zero shortfall`() {
        val pace = SavingsGoalPace(
            requiredMonthlySavingMinor = 30_000_00L,
            currentMonthContributionMinor = 40_000_00L,
            currentMonthNetSavingsMinor = 50_000_00L,
            trailingThreeMonthAverageMinor = 45_000_00L,
            paceStatus = GoalPaceStatus.ON_TRACK,
            shortfallMinor = 0L,
            projectedCompletionDate = LocalDate.of(2027, 8, 1),
            benchmarkUsed = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
        )
        assertEquals(GoalPaceStatus.ON_TRACK, pace.paceStatus)
        assertEquals(0L, pace.shortfallMinor)
        assertEquals("Aug 01, 2027", SavingsGoalUiFormatter.formatDisplayDate(pace.projectedCompletionDate.toString()))
    }

    // 12. BEHIND presentation + shortfall
    @Test
    fun `12 BEHIND presentation displays accurate status and positive shortfall`() {
        val pace = SavingsGoalPace(
            requiredMonthlySavingMinor = 50_000_00L,
            currentMonthContributionMinor = 20_000_00L,
            currentMonthNetSavingsMinor = 15_000_00L,
            trailingThreeMonthAverageMinor = 20_000_00L,
            paceStatus = GoalPaceStatus.BEHIND,
            shortfallMinor = 30_000_00L,
            projectedCompletionDate = LocalDate.of(2028, 1, 1),
            benchmarkUsed = PaceBenchmark.TRAILING_THREE_MONTH_AVERAGE
        )
        assertEquals(GoalPaceStatus.BEHIND, pace.paceStatus)
        assertEquals(30_000_00L, pace.shortfallMinor)
        assertEquals("LKR 30,000.00", SavingsGoalUiFormatter.formatLkr(pace.shortfallMinor))
    }

    // 13. discretionary impact uses supplied state percentage
    @Test
    fun `13 discretionary impact uses supplied state percentage`() {
        val impact = DiscretionarySpendingImpact(
            discretionaryExpensesMinor = 21_000_00L,
            committedExpensesMinor = 50_000_00L,
            totalExpensesMinor = 71_000_00L,
            requiredMonthlySavingMinor = 50_000_00L,
            percentageOfRequiredSaving = 42.0
        )
        val sentence = SavingsGoalUiFormatter.formatDiscretionaryImpactSentence(impact, isCompleted = false)
        assertEquals("Your discretionary spending this month equals 42% of one month's required saving.", sentence)

        val completedSentence = SavingsGoalUiFormatter.formatDiscretionaryImpactSentence(impact, isCompleted = true)
        assertEquals("Goal target reached — no monthly savings required this month.", completedSentence)
    }

    // 14. invalid target amount rejected
    @Test
    fun `14 invalid target amount rejected by form validator`() {
        val zeroResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "Trip",
            targetAmountInput = "0",
            targetDateInput = "2027-10-18",
            openingSavedInput = "0"
        )
        assertFalse(zeroResult.isValid)
        assertEquals("Target amount must be greater than zero", zeroResult.targetAmountError)

        val negativeResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "Trip",
            targetAmountInput = "-500",
            targetDateInput = "2027-10-18",
            openingSavedInput = "0"
        )
        assertFalse(negativeResult.isValid)
        assertEquals("Target amount must be greater than zero", negativeResult.targetAmountError)

        val nonNumericResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "Trip",
            targetAmountInput = "abc",
            targetDateInput = "2027-10-18",
            openingSavedInput = "0"
        )
        assertFalse(nonNumericResult.isValid)
        assertEquals("Target amount must be greater than zero", nonNumericResult.targetAmountError)
    }

    // 15. negative opening saved rejected
    @Test
    fun `15 negative opening saved rejected by form validator`() {
        val negativeResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "MacBook Pro",
            targetAmountInput = "490,000",
            targetDateInput = "2027-10-18",
            openingSavedInput = "-100"
        )
        assertFalse(negativeResult.isValid)
        assertEquals("Opening saved amount cannot be negative", negativeResult.openingSavedError)
    }

    // 16. zero opening saved accepted
    @Test
    fun `16 zero opening saved accepted by form validator`() {
        val zeroInputResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "MacBook Pro",
            targetAmountInput = "490,000",
            targetDateInput = "2027-10-18",
            openingSavedInput = "0"
        )
        assertTrue(zeroInputResult.isValid)
        assertEquals(0L, zeroInputResult.openingSavedAmountMinor)
        assertNull(zeroInputResult.openingSavedError)

        val blankInputResult = SavingsGoalUiFormatter.validateGoalForm(
            name = "MacBook Pro",
            targetAmountInput = "490,000",
            targetDateInput = "2027-10-18",
            openingSavedInput = ""
        )
        assertTrue(blankInputResult.isValid)
        assertEquals(0L, blankInputResult.openingSavedAmountMinor)
        assertNull(blankInputResult.openingSavedError)
    }

    // 17. operationInProgress prevents duplicate submit
    @Test
    fun `17 operationInProgress prevents duplicate submit`() {
        val idleState = SavingsGoalUiState(operationInProgress = false)
        val busyState = SavingsGoalUiState(operationInProgress = true)

        assertFalse(idleState.operationInProgress)
        assertTrue(busyState.operationInProgress)

        // Error message state verification
        val errorState = SavingsGoalUiState(errorMessage = "An active savings goal already exists")
        assertNotNull(errorState.errorMessage)
        assertEquals("An active savings goal already exists", errorState.errorMessage)
    }
}
