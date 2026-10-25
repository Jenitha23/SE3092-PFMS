package com.pfms.app.viewmodel

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.savings.InsufficientGoalSavingsException
import com.pfms.app.domain.usecase.savings.ValidateGoalLedgerUseCase
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SavingsWithdrawalValidationTest {
    private val goal = SavingsGoal("goal", "user", "Emergency fund", 49000000, "2027-10-25", 1120000)
    private val contribution = GoalTransaction("deposit", goal.id, goal.userId, 1500000,
        GoalTransactionType.CONTRIBUTION, "2026-10-01", createdAt = 1)
    private val withdrawal = GoalTransaction("withdrawal", goal.id, goal.userId, 500000,
        GoalTransactionType.WITHDRAWAL, "2026-10-02", createdAt = 2)
    private val ledger = listOf(contribution, withdrawal)
    private fun source(transactions: List<GoalTransaction> = ledger) = SavingsGoalUiState(isLoading = false,
        activeGoal = goal, transactions = transactions, currentSavedMinor = ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, transactions))
    private fun controller(state: SavingsGoalUiState = source()) = SavingsGoalScreenViewModel().apply {
        today = { LocalDate.of(2026, 10, 25) }; sync(state); openTransaction(GoalTransactionType.WITHDRAWAL)
    }
    private fun candidate(date: String = "2026-10-25") = GoalTransaction("new", goal.id, goal.userId,
        1, GoalTransactionType.WITHDRAWAL, date, createdAt = 3)
    private fun SavingsGoalScreenViewModel.submit(save: (Long) -> Unit = { fail("Invalid input must not reach persistence") }) =
        submitTransaction({ _, _, _ -> fail("Wrong transaction type") }, { amount, _, _ -> save(amount) }, { fail("Wrong operation") })

    @Test fun reportExampleUsesOpeningContributionsAndWithdrawalsAndRejects25000() {
        val vm = controller(source().copy(currentSavedMinor = 999)) // Ledger is authoritative, not a stale presentation total.
        assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.available)
        vm.transactionAmount("25,000.00")
        vm.submit()
        assertEquals("Insufficient savings. You can withdraw up to LKR 21,200.00.", vm.state.value.transactionDraft?.errors?.amountError)
        assertEquals("25000.00", vm.state.value.transactionDraft?.amount?.replace(",", ""))
        assertEquals(ledger, vm.state.value.source.transactions)
        assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.available)
    }

    @Test fun correctingAnOverdraftClearsErrorAndSubmitsExactDecimalMinorUnits() {
        val vm = controller()
        vm.transactionAmount("25000"); vm.submit()
        vm.transactionAmount("1500.75")
        assertNull(vm.state.value.transactionDraft?.errors)
        var amount: Long? = null
        vm.submit { amount = it }
        assertEquals(150075L, amount)
        assertNotNull(vm.state.value.transactionDraft) // Only persistence success closes the form.
        assertEquals(2120000L, vm.state.value.source.currentSavedMinor)
        vm.sync(source().copy(successVersion = 1))
        assertNull(vm.state.value.transactionDraft)
    }

    @Test fun emptyZeroNegativeNonNumericOverflowAndFractionalCentsNeverSubmit() {
        val vm = controller()
        for (input in listOf("", " ", "0", "0.00", "-1", "abc", ".", "1.001", "999999999999999999999")) {
            vm.transactionAmount(input); vm.submit()
            assertNotNull("Input: $input", vm.state.value.transactionDraft?.errors?.amountError)
            assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.available)
        }
        vm.transactionAmount("21,200.00")
        var amount: Long? = null
        vm.submit { amount = it }
        assertEquals(2120000L, amount)
    }

    @Test fun editingAWithdrawalReplacesTheOriginalRatherThanSubtractingItTwice() {
        val vm = controller()
        vm.openTransaction(withdrawal.transactionType, withdrawal)
        assertEquals("LKR 26,200.00", vm.state.value.transactionDraft?.available)
        assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.currentSavings)
        vm.transactionAmount("30000")
        vm.submitTransaction({ _, _, _ -> fail() }, { _, _, _ -> fail() }, { fail() })
        assertNotNull(vm.state.value.transactionDraft?.errors?.amountError)
        vm.transactionAmount("25000")
        var updated: GoalTransaction? = null
        vm.submitTransaction({ _, _, _ -> fail() }, { _, _, _ -> fail() }, { updated = it })
        assertEquals(withdrawal.id, updated?.id)
        assertEquals(withdrawal.createdAt, updated?.createdAt)
        assertEquals(2500000L, updated?.amountMinor)
        assertEquals(120000L, ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, listOf(contribution, updated!!)))
    }

    @Test fun deletingAWithdrawalAndReceivingLedgerChangesRefreshTheAvailableAmount() {
        val vm = controller()
        vm.transactionAmount("25000"); vm.submit()
        vm.sync(source(listOf(contribution)))
        assertEquals("LKR 26,200.00", vm.state.value.transactionDraft?.available)
        assertTrue(vm.state.value.transactionDraft?.errors?.isValid == true)
        vm.submit { assertEquals(2500000L, it) }
        vm.sync(source())
        assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.available)
    }

    @Test fun backdatedAvailabilityAgreesWithTheAuthoritativeLedgerValidator() {
        val early = candidate("2026-09-30")
        val available = ValidateGoalLedgerUseCase.availableForWithdrawal(goal.openingSavedAmountMinor, ledger, early)
        assertEquals(1120000L, available)
        assertEquals(1000000L, ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, ledger + early.copy(amountMinor = available)))
        assertThrows(InsufficientGoalSavingsException::class.java) {
            ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, ledger + early.copy(amountMinor = available + 1))
        }
    }

    @Test fun futureWithdrawalsKeepTheirFundingAndLaterContributionsCannotBeSpentEarly() {
        val laterWithdrawal = withdrawal.copy(date = "2026-10-30", amountMinor = 1000000)
        val laterDeposit = contribution.copy(date = "2026-10-31", amountMinor = 2000000)
        val transactions = listOf(laterWithdrawal, laterDeposit)
        val available = ValidateGoalLedgerUseCase.availableForWithdrawal(goal.openingSavedAmountMinor, transactions, candidate())
        assertEquals(120000L, available)
        ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, transactions + candidate().copy(amountMinor = available))
        assertThrows(InsufficientGoalSavingsException::class.java) {
            ValidateGoalLedgerUseCase.validate(goal.openingSavedAmountMinor, transactions + candidate().copy(amountMinor = available + 1))
        }
    }

    @Test fun unrelatedGoalAndUserTransactionsDoNotAffectAvailability() {
        val vm = controller(source().copy(transactions = ledger + contribution.copy(id = "other-goal", goalId = "other") +
            contribution.copy(id = "other-user", userId = "other")))
        assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.available)
    }

    @Test fun anInvalidLedgerIsReportedAndNeverHiddenByAZeroBalance() {
        val invalid = listOf(withdrawal.copy(amountMinor = 2000000))
        val vm = controller(SavingsGoalUiState(isLoading = false, activeGoal = goal, transactions = invalid, currentSavedMinor = 0))
        assertNull(vm.state.value.transactionDraft?.available)
        assertNotNull(vm.state.value.transactionDraft?.balanceError)
        vm.transactionAmount("1"); vm.submit()
        assertNotNull(vm.state.value.transactionDraft?.errors?.amountError)
        assertEquals(invalid, vm.state.value.source.transactions)
    }

    @Test fun compatibilityAddDialogUsesItsSuppliedBalanceAndUnknownEditDoesNotInventZero() {
        val vm = controller(SavingsGoalUiState(isLoading = false, currentSavedMinor = 2120000))
        vm.openTransaction(GoalTransactionType.WITHDRAWAL, balanceProvided = true)
        assertEquals("LKR 21,200.00", vm.state.value.transactionDraft?.available)
        vm.transactionAmount("25000"); vm.submit()
        assertNotNull(vm.state.value.transactionDraft?.errors?.amountError)
        vm.openTransaction(GoalTransactionType.WITHDRAWAL, withdrawal)
        assertNull(vm.state.value.transactionDraft?.available)
    }
}
