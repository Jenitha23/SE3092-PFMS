package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.savings.DiscretionarySpendingImpact
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Pure calculation use case implementing FR-27:
 * Expresses current month discretionary spending as a percentage of one month's required saving.
 *
 * Rules:
 * - Only includes expenses where [Expense.classification] is [ExpenseClassification.DISCRETIONARY].
 * - Strictly excludes [ExpenseClassification.COMMITTED] expenses from discretionary total.
 * - Handles requiredMonthlySaving <= 0 safely by returning 0.0% (avoids division by zero).
 * - Handles empty expense lists and malformed date strings defensively.
 */
class CalculateDiscretionaryImpactUseCase @Inject constructor() {

    operator fun invoke(
        expenses: List<Expense>,
        selectedMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Colombo")),
        requiredMonthlySavingMinor: Long
    ): DiscretionarySpendingImpact {
        var discretionaryMinor = 0L
        var committedMinor = 0L

        for (expense in expenses) {
            val expenseMonth = try {
                YearMonth.from(LocalDate.parse(expense.date))
            } catch (e: DateTimeParseException) {
                null
            }

            if (expenseMonth == selectedMonth) {
                when (expense.classification) {
                    ExpenseClassification.DISCRETIONARY -> discretionaryMinor += expense.amountMinor
                    ExpenseClassification.COMMITTED -> committedMinor += expense.amountMinor
                }
            }
        }

        val totalExpensesMinor = discretionaryMinor + committedMinor

        val percentage = if (requiredMonthlySavingMinor <= 0L || discretionaryMinor <= 0L) {
            0.0
        } else {
            SafeCalculations.percentage(discretionaryMinor, requiredMonthlySavingMinor)
        }

        return DiscretionarySpendingImpact(
            discretionaryExpensesMinor = discretionaryMinor,
            committedExpensesMinor = committedMinor,
            totalExpensesMinor = totalExpensesMinor,
            requiredMonthlySavingMinor = requiredMonthlySavingMinor,
            percentageOfRequiredSaving = percentage
        )
    }
}
