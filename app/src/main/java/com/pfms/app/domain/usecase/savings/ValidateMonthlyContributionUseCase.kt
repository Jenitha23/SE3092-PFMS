package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
import java.time.YearMonth

/** Monthly cash-flow policy, not a bank account or verified available-funds balance. */
object ValidateMonthlyContributionUseCase {
    fun allowanceMinor(
        month: YearMonth,
        incomes: List<Income>,
        expenses: List<Expense>,
        activeGoalTransactions: List<GoalTransaction>
    ): Long {
        val net = MonthlyFinanceCalculator.summarize(incomes, expenses, month).netSavingsMinor
        val allocated = activeGoalTransactions.asSequence()
            .filter { runCatching { YearMonth.from(java.time.LocalDate.parse(it.date)) }.getOrNull() == month }
            .fold(0L) { total, tx ->
                val signed = when (tx.transactionType) {
                    GoalTransactionType.CONTRIBUTION -> tx.amountMinor
                    GoalTransactionType.WITHDRAWAL -> Math.negateExact(tx.amountMinor)
                }
                Math.addExact(total, signed)
            }
        // Subtract previous net contributions so the same surplus is not allocated twice.
        val unbounded = Math.subtractExact(net, allocated)
        return unbounded.coerceAtLeast(0L)
    }

    fun requireContributionAllowed(
        candidate: GoalTransaction,
        incomes: List<Income>,
        expenses: List<Expense>,
        existingTransactions: List<GoalTransaction>
    ) {
        if (candidate.transactionType != GoalTransactionType.CONTRIBUTION) return
        require(candidate.amountMinor > 0L) { "Contribution must be greater than zero" }
        val month = YearMonth.from(java.time.LocalDate.parse(candidate.date))
        val allowance = allowanceMinor(month, incomes, expenses, existingTransactions)
        if (candidate.amountMinor > allowance) {
            throw InsufficientMonthlySurplusException(allowance, candidate.amountMinor, month.toString())
        }
    }
}

class InsufficientMonthlySurplusException(
    val availableMinor: Long,
    val requestedMinor: Long,
    val month: String
) : IllegalArgumentException("Insufficient recorded monthly surplus")
