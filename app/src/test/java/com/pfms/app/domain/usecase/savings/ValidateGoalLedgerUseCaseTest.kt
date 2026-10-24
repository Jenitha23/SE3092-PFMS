package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ValidateGoalLedgerUseCaseTest {
    private fun tx(id: String, amount: Long, type: GoalTransactionType, date: String = "2026-10-24") =
        GoalTransaction(id=id, goalId="goal1", userId="test", amountMinor=amount,
            transactionType=type, date=date)

    @Test fun exactBalanceWithdrawalAllowed() {
        assertEquals(0L, ValidateGoalLedgerUseCase.validate(1_120_000L,
            listOf(tx("w", 1_120_000L, GoalTransactionType.WITHDRAWAL))))
    }
    @Test fun withdrawalGreaterThanBalanceRejected() {
        assertThrows(InsufficientGoalSavingsException::class.java) {
            ValidateGoalLedgerUseCase.validate(1_120_000L,
                listOf(tx("w",1_500_000L,GoalTransactionType.WITHDRAWAL)))
        }
    }
    @Test fun deletionOfEarlierContributionRejectedWhenWithdrawalDependsOnIt() {
        val withdrawal = tx("w", 1_500_000L,GoalTransactionType.WITHDRAWAL)
        assertEquals(120_000L, ValidateGoalLedgerUseCase.validate(1_120_000L,
            listOf(tx("c",500_000L,GoalTransactionType.CONTRIBUTION),withdrawal)))
        assertThrows(InsufficientGoalSavingsException::class.java) {
            ValidateGoalLedgerUseCase.validate(1_120_000L,listOf(withdrawal))
        }
    }
    @Test fun backdatedWithdrawalCannotOverdrawBeforeLaterDeposit() {
        assertThrows(InsufficientGoalSavingsException::class.java) {
            ValidateGoalLedgerUseCase.validate(0L, listOf(
                tx("c",5_000L,GoalTransactionType.CONTRIBUTION,"2026-10-25"),
                tx("w",1_000L,GoalTransactionType.WITHDRAWAL,"2026-10-24")))
        }
    }
    @Test fun invalidZeroAmountRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ValidateGoalLedgerUseCase.validate(0L,listOf(tx("c",0L,GoalTransactionType.CONTRIBUTION)))
        }
    }
    @Test fun contributionMayExceedMonthlySurplusBecauseItIsNotBankBalance() {
        assertEquals(2_000_000L,ValidateGoalLedgerUseCase.validate(0L,
            listOf(tx("c",2_000_000L,GoalTransactionType.CONTRIBUTION))))
    }
}
