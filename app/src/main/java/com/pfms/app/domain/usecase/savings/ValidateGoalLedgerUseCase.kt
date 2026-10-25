package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import java.time.LocalDate

/** Checks the entire time-ordered ledger; rejects any historical overdraft. */
object ValidateGoalLedgerUseCase {
    private val order = compareBy<GoalTransaction> { LocalDate.parse(it.date) }.thenBy { it.createdAt }.thenBy { it.id }

    fun validate(openingMinor: Long, transactions: List<GoalTransaction>): Long {
        require(openingMinor >= 0L) { "Opening savings cannot be negative" }
        var balance = openingMinor
        for (tx in transactions.sortedWith(order)) balance = applyTransaction(balance, tx)
        return balance
    }

    /**
     * Maximum replacement/new withdrawal without overdrawing at its date or any later entry.
     * Removing the original by ID prevents counting an edited withdrawal twice. Later deposits
     * cannot fund an earlier withdrawal, and existing later withdrawals retain their funding.
     */
    fun availableForWithdrawal(openingMinor: Long, transactions: List<GoalTransaction>, candidate: GoalTransaction): Long {
        require(openingMinor >= 0L) { "Opening savings cannot be negative" }
        require(candidate.transactionType == GoalTransactionType.WITHDRAWAL)
        LocalDate.parse(candidate.date)
        var balance = openingMinor
        var available: Long? = null
        for (tx in transactions.filterNot { it.id == candidate.id }.sortedWith(order)) {
            if (available == null && order.compare(tx, candidate) >= 0) available = balance
            balance = applyTransaction(balance, tx)
            if (available != null) available = minOf(available, balance)
        }
        return available ?: balance
    }

    private fun applyTransaction(balance: Long, tx: GoalTransaction): Long {
        require(tx.amountMinor > 0L) { "Amount must be greater than zero" }
        return when (tx.transactionType) {
            GoalTransactionType.CONTRIBUTION -> Math.addExact(balance, tx.amountMinor)
            GoalTransactionType.WITHDRAWAL -> {
                if (tx.amountMinor > balance) throw InsufficientGoalSavingsException(balance, tx.amountMinor)
                balance - tx.amountMinor
            }
        }
    }
}

class InsufficientGoalSavingsException(val availableMinor: Long, val requestedMinor: Long) :
    IllegalArgumentException("Insufficient goal savings")
