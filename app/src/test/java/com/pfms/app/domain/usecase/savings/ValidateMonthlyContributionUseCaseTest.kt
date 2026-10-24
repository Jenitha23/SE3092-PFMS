package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.Income
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

class ValidateMonthlyContributionUseCaseTest {
    private val month = YearMonth.of(2026, 10)
    private fun income(value: Long) = Income(amountMinor = value, receivedDate = "2026-10-24")
    private fun expense(value: Long) = Expense(amountMinor = value, date = "2026-10-24")
    private fun contribution(value: Long) = GoalTransaction(id = "c", amountMinor = value,
        date = "2026-10-24", transactionType = GoalTransactionType.CONTRIBUTION)
    private fun withdrawal(value: Long) = GoalTransaction(id = "w", amountMinor = value,
        date = "2026-10-24", transactionType = GoalTransactionType.WITHDRAWAL)

    @Test fun positiveSurplusAllowsContribution() {
        ValidateMonthlyContributionUseCase.requireContributionAllowed(contribution(2_000_000),
            listOf(income(5_000_000)), listOf(expense(2_000_000)), emptyList())
    }

    @Test fun previousContributionsReduceAllowance() {
        assertEquals(2_500_000L, ValidateMonthlyContributionUseCase.allowanceMinor(month,
            listOf(income(10_000_000)), listOf(expense(6_000_000)), listOf(contribution(1_500_000))))
        assertThrows(InsufficientMonthlySurplusException::class.java) {
            ValidateMonthlyContributionUseCase.requireContributionAllowed(contribution(3_000_000),
                listOf(income(10_000_000)), listOf(expense(6_000_000)), listOf(contribution(1_500_000)))
        }
    }

    @Test fun deficitRejectsContribution() {
        assertThrows(InsufficientMonthlySurplusException::class.java) {
            ValidateMonthlyContributionUseCase.requireContributionAllowed(contribution(100),
                listOf(income(10_000)), listOf(expense(15_000)), emptyList())
        }
    }

    @Test fun exactlyRemainingAllowanceIsAllowed() {
        ValidateMonthlyContributionUseCase.requireContributionAllowed(contribution(2_500_000),
            listOf(income(10_000_000)), listOf(expense(6_000_000)), listOf(contribution(1_500_000)))
    }

    @Test fun withdrawalsFreeUpMonthlyContributionAllowance() {
        assertEquals(3_000_000L, ValidateMonthlyContributionUseCase.allowanceMinor(month,
            listOf(income(5_000_000)), listOf(expense(2_000_000)),
            listOf(contribution(2_000_000), withdrawal(2_000_000))))
    }

    @Test fun expectedFreelanceExcluded() {
        val expected = Income(amountMinor = 6_000_000, source = "Freelance", paymentStatus = "Expected", receivedDate = "2026-10-24")
        assertEquals(0L, ValidateMonthlyContributionUseCase.allowanceMinor(month,
            listOf(expected), emptyList(), emptyList()))
    }
}
