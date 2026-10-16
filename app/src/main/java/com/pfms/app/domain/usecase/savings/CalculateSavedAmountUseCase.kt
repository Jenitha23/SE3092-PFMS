package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import javax.inject.Inject

/**
 * Pure calculation use case implementing FR-24:
 * currentSaved = openingSavedAmount + contributions - withdrawals
 *
 * Rules:
 * - Only includes transactions strictly belonging to the given goal (and matching userId if specified).
 * - Ignores transactions belonging to other goals.
 * - Monthly surplus is NEVER automatically added (only explicit ledger transactions count).
 * - Handles empty ledger safely by returning [SavingsGoal.openingSavedAmountMinor].
 * - Domain safety policy: If total withdrawals exceed (openingSavedAmount + contributions),
 *   currentSaved is safely clamped to 0L to prevent negative savings balances and underflow.
 */
class CalculateSavedAmountUseCase @Inject constructor() {

    operator fun invoke(
        goal: SavingsGoal,
        transactions: List<GoalTransaction>
    ): Long {
        val goalTransactions = transactions.filter { tx ->
            tx.goalId == goal.id && (goal.userId.isBlank() || tx.userId.isBlank() || tx.userId == goal.userId)
        }

        var balance = goal.openingSavedAmountMinor

        for (tx in goalTransactions) {
            when (tx.transactionType) {
                GoalTransactionType.CONTRIBUTION -> balance += tx.amountMinor
                GoalTransactionType.WITHDRAWAL -> balance -= tx.amountMinor
            }
        }

        // Domain decision: Savings balance cannot be negative.
        return maxOf(0L, balance)
    }
}
