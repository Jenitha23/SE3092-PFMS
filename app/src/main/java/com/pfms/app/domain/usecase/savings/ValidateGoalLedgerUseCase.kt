package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import java.time.LocalDate

/** Checks the entire time-ordered ledger; rejects any historical overdraft. */
object ValidateGoalLedgerUseCase {
    fun validate(openingMinor: Long, transactions: List<GoalTransaction>): Long {
        require(openingMinor >= 0L) { "Opening savings cannot be negative" }
        var balance = openingMinor
        for (tx in transactions.sortedWith(compareBy<GoalTransaction> { LocalDate.parse(it.date) }.thenBy { it.createdAt }.thenBy { it.id })) {
            require(tx.amountMinor > 0L) { "Amount must be greater than zero" }
            balance = when (tx.transactionType) {
                GoalTransactionType.CONTRIBUTION -> Math.addExact(balance, tx.amountMinor)
                GoalTransactionType.WITHDRAWAL -> {
                    if (tx.amountMinor > balance) throw InsufficientGoalSavingsException(balance, tx.amountMinor)
                    balance - tx.amountMinor
                }
            }
        }
        return balance
    }
}

class InsufficientGoalSavingsException(val availableMinor: Long, val requestedMinor: Long) :
    IllegalArgumentException("Insufficient goal savings")
