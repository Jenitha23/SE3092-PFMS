package com.pfms.app.viewmodel

import com.pfms.app.data.error.OperationErrors
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.savings.InsufficientMonthlySurplusException
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SavingsContributionStateTest {
    private val source = SavingsGoalUiState(isLoading = false,
        activeGoal = SavingsGoal("goal", "user", "Fund", 10000000, "2027-10-25", 1000000), currentSavedMinor = 3000000)
    private fun controller() = SavingsGoalScreenViewModel().apply {
        today = { LocalDate.of(2026, 10, 25) }; sync(source); openTransaction(GoalTransactionType.CONTRIBUTION)
    }

    @Test fun typedSurplusErrorIsReadableAndDoesNotExposeWrappedBackendDetails() {
        val error = OperationErrors.present(IllegalStateException("Private database details",
            InsufficientMonthlySurplusException(1000000, 1500000, "2026-10")))
        assertFalse(error.canRetry)
        assertTrue(error.message.contains("remaining recorded monthly surplus"))
        assertTrue(error.message.contains("2026-10"))
        assertTrue(error.message.contains("Available: LKR 10,000.00"))
        assertTrue(error.message.contains("requested: LKR 15,000.00"))
        assertTrue(error.message.contains("not your bank balance"))
        assertFalse(error.message.contains("Private"))
    }

    @Test fun rejectionRetainsTheDraftAndCorrectionCanSubmitTheCurrentAmount() {
        val vm = controller()
        var amount: Long? = null
        vm.transactionAmount("15000")
        vm.submitTransaction({ value, _, _ -> amount = value }, { _, _, _ -> fail() }, { fail() })
        assertEquals(1500000L, amount) // No independent financial policy calculation in the form.
        val error = OperationErrors.present(InsufficientMonthlySurplusException(1000000, 1500000, "2026-10"))
        vm.sync(source.copy(errorMessage = error.message, canRetry = error.canRetry))
        assertEquals("15000", vm.state.value.transactionDraft?.amount)
        assertFalse(vm.state.value.transactionDraft!!.submissionPending)
        assertEquals(3000000L, vm.state.value.source.currentSavedMinor)
        vm.transactionAmount("9999.25")
        vm.sync(source)
        vm.submitTransaction({ value, _, _ -> amount = value }, { _, _, _ -> fail() }, { fail() })
        assertEquals(999925L, amount)
    }

    @Test fun synchronousSubmissionGuardClosesTheGapBeforeLoadingAndSuccessAreCollected() {
        val vm = controller()
        vm.transactionAmount("100")
        var attempts = 0
        repeat(3) { vm.submitTransaction({ _, _, _ -> attempts++ }, { _, _, _ -> fail() }, { fail() }) }
        assertEquals(1, attempts)
        assertTrue(vm.state.value.transactionDraft!!.submissionPending)
        vm.transactionAmount("999"); vm.dismissTransaction()
        assertEquals("100", vm.state.value.transactionDraft?.amount)
        vm.sync(source) // A late unchanged idle snapshot must not release the guard.
        vm.submitTransaction({ _, _, _ -> attempts++ }, { _, _, _ -> fail() }, { fail() })
        assertEquals(1, attempts)
        vm.sync(source.copy(operationInProgress = true))
        vm.sync(source.copy(successVersion = 1, currentSavedMinor = 3010000, progressPercent = 30.1))
        assertNull(vm.state.value.transactionDraft)
        assertEquals("LKR 30,100.00", vm.state.value.display.saved)
        vm.openTransaction(GoalTransactionType.CONTRIBUTION)
        assertFalse(vm.state.value.transactionDraft!!.submissionPending)
    }

    @Test fun invalidInputsNeverStartSavingAndLoadingBlocksSubmission() {
        val vm = controller()
        for (amount in listOf("", "0", "-1", "bad", "1.001", "999999999999999999999")) {
            vm.transactionAmount(amount)
            vm.submitTransaction({ _, _, _ -> fail() }, { _, _, _ -> fail() }, { fail() })
            assertNotNull(vm.state.value.transactionDraft?.errors?.amountError)
            assertFalse(vm.state.value.transactionDraft!!.submissionPending)
        }
        vm.transactionAmount("100")
        vm.sync(source.copy(isLoading = true))
        vm.submitTransaction({ _, _, _ -> fail() }, { _, _, _ -> fail() }, { fail() })
        assertFalse(vm.state.value.transactionDraft!!.submissionPending)
    }
}
