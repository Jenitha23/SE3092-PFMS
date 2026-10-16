package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Pure calculation use case implementing FR-26 (Goal Ledger Activity):
 * Calculates net goal contributions within a specific calendar month.
 *
 * Formula:
 * currentMonthGoalContribution = sum(CONTRIBUTIONS in month) - sum(WITHDRAWALS in month)
 *
 * IMPORTANT DISTINCTION:
 * This represents actual deposits and withdrawals committed to the GOAL LEDGER for the given month.
 * It is distinct from general financial net savings (income - expenses).
 */
class CalculateCurrentMonthGoalContributionUseCase @Inject constructor() {

    operator fun invoke(
        goalId: String,
        transactions: List<GoalTransaction>,
        targetMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo"))
    ): Long {
        if (goalId.isBlank() || transactions.isEmpty()) {
            return 0L
        }

        val monthlyTransactions = transactions.filter { tx ->
            if (tx.goalId != goalId) return@filter false
            val txMonth = try {
                YearMonth.from(LocalDate.parse(tx.date))
            } catch (e: DateTimeParseException) {
                null
            }
            txMonth == targetMonth
        }

        var netContribution = 0L
        for (tx in monthlyTransactions) {
            when (tx.transactionType) {
                GoalTransactionType.CONTRIBUTION -> netContribution += tx.amountMinor
                GoalTransactionType.WITHDRAWAL -> netContribution -= tx.amountMinor
            }
        }

        return netContribution
    }
}
