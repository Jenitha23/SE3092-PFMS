package com.pfms.app.viewmodel

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.savings.SavingsGoalEntryRules
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class SavingsGoalRedesignStateTest {
    private fun controller(source: SavingsGoalUiState = SavingsGoalUiState(isLoading = false)) = SavingsGoalScreenViewModel().apply {
        today = { LocalDate.of(2026, 10, 24) }; sync(source)
    }
    @Test fun previewUsesTheEnteredTargetSavingsAndCalendarMonths() {
        val vm = controller()
        vm.openGoal()
        assertEquals("2027-10-24", vm.state.value.goalDraft?.date)
        assertEquals("LKR 39,900.00 / mo", vm.state.value.goalDraft?.monthlyPreview)
        vm.goalTarget("100000.00"); vm.goalOpening("40000.00")
        assertEquals("LKR 5,000.00 / mo", vm.state.value.goalDraft?.monthlyPreview)
        vm.goalOpening("100000.00")
        assertEquals("Target already funded", vm.state.value.goalDraft?.monthlyPreview)
    }
    @Test fun editPreviewIncludesTheExistingLedgerAndKeepsGoalIdentity() {
        val goal = SavingsGoal("goal", "user", "Upgrade", 10000000, "2027-10-24", 1000000)
        val tx = GoalTransaction("deposit", "goal", "user", 3000000, date = "2026-10-24")
        val vm = controller(SavingsGoalUiState(isLoading = false, activeGoal = goal, transactions = listOf(tx)))
        vm.openGoal(editing = true)
        assertEquals("LKR 5,000.00 / mo", vm.state.value.goalDraft?.monthlyPreview)
        var saved: SavingsGoal? = null
        vm.goalName("Laptop"); vm.submitGoal({ _, _, _, _ -> fail("Must update existing goal") }, { saved = it })
        assertEquals("goal", saved?.id); assertEquals("user", saved?.userId); assertEquals("Laptop", saved?.name)
    }
    @Test fun invalidPrecisionAndDatesDoNotSubmitAndFailuresRetainTheDraft() {
        val vm = controller()
        vm.openGoal(); vm.goalTarget("0.001")
        var attempts = 0
        vm.submitGoal({ _, _, _, _ -> attempts++ }, {})
        assertEquals(0, attempts); assertNotNull(vm.state.value.goalDraft?.errors?.targetAmountError)
        vm.goalTarget("250000.25"); vm.goalName("Emergency fund")
        vm.submitGoal({ _, _, _, _ -> attempts++ }, {})
        vm.sync(SavingsGoalUiState(isLoading = false, errorMessage = "Offline", canRetry = true))
        assertEquals("250000.25", vm.state.value.goalDraft?.target)
        assertEquals("Emergency fund", vm.state.value.goalDraft?.name)
        vm.submitGoal({ _, _, _, _ -> attempts++ }, {})
        assertEquals(2, attempts)
        vm.sync(SavingsGoalUiState(isLoading = false, successVersion = 1))
        assertNull(vm.state.value.goalDraft)
        assertFalse(SavingsGoalEntryRules.validateGoal("Test", "100", "2027-02-30", "0").isValid)
        assertNull(SavingsGoalEntryRules.parseAmount("999999999999999999999"))
    }
    @Test fun goalIdeasPrefillANameWithoutInventingATargetAmount() {
        val vm = controller()
        vm.openGoal(idea = "safety")
        assertEquals("Emergency Fund", vm.state.value.goalDraft?.name)
        assertEquals("", vm.state.value.goalDraft?.target)
        assertEquals("0.00", vm.state.value.goalDraft?.opening)
        assertNull(vm.state.value.goalDraft?.monthlyPreview)
    }
    @Test fun transactionEditPreservesItsTypeDateAndLedgerIdentity() {
        val vm = controller()
        val original = GoalTransaction("tx", "goal", "user", 10000, GoalTransactionType.WITHDRAWAL, "2026-10-01", "Old note", 99)
        vm.openTransaction(original.transactionType, original)
        vm.transactionAmount("250.75"); vm.transactionNote(" Updated note ")
        var saved: GoalTransaction? = null
        vm.submitTransaction({ _, _, _ -> fail() }, { _, _, _ -> fail() }, { saved = it })
        assertEquals("tx", saved?.id); assertEquals("goal", saved?.goalId); assertEquals(99L, saved?.createdAt)
        assertEquals(GoalTransactionType.WITHDRAWAL, saved?.transactionType); assertEquals("2026-10-01", saved?.date)
        assertEquals(25075L, saved?.amountMinor); assertEquals("Updated note", saved?.note)
    }
}
