
package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

object ExpenseRules {

    private const val DUPLICATE_WINDOW_MS = 60_000L

    /**
     * Validate an expense amount and convert it to
     * integer minor units.
     *
     * Example:
     * "1250.50" -> 125050L
     */
    fun parseAmount(amount: String): Long? {
        return try {
            val text = amount.trim()

            if (!text.matches(
                    Regex("""\d+(\.\d{1,2})?""")
                )
            ) {
                return null
            }

            val decimal = BigDecimal(text)

            if (decimal <= BigDecimal.ZERO) {
                return null
            }

            decimal.movePointRight(2).longValueExact()

        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks whether the required expense fields
     * contain valid information.
     *
     * Returns null when validation succeeds.
     */
    fun validateExpense(
        amountMinor: Long,
        categoryId: String,
        date: String
    ): String? {

        if (amountMinor <= 0L) {
            return "Enter a valid expense amount."
        }

        if (categoryId.isBlank()) {
            return "Select an expense category."
        }

        try {
            LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            return "Select a valid expense date."
        }

        return null
    }

    /**
     * Applies a category's default classification.
     *
     * If a user manually overrides the classification,
     * the selected value takes priority.
     */
    fun resolveClassification(
        categoryDefault: ExpenseClassification,
        userOverride: ExpenseClassification?
    ): ExpenseClassification {
        return userOverride ?: categoryDefault
    }

    /**
     * Returns true if an identical expense already
     * exists within the previous 60 seconds.
     *
     * This is based on transaction fields and the
     * record creation timestamp.
     */
    fun isDuplicateWithin60Seconds(
        newExpense: Expense,
        recentExpenses: List<Expense>,
        nowMillis: Long
    ): Boolean {

        return recentExpenses.any { existing ->

            val ageMillis =
                nowMillis - existing.createdAt

            val isWithinWindow =
                ageMillis in 0..DUPLICATE_WINDOW_MS

            val isIdentical =
                existing.amountMinor ==
                        newExpense.amountMinor &&
                        existing.currency ==
                        newExpense.currency &&
                        existing.categoryId ==
                        newExpense.categoryId &&
                        existing.classification ==
                        newExpense.classification &&
                        existing.paymentMethod ==
                        newExpense.paymentMethod &&
                        existing.date ==
                        newExpense.date &&
                        existing.merchant.trim() ==
                        newExpense.merchant.trim() &&
                        existing.note.trim() ==
                        newExpense.note.trim()

            isWithinWindow && isIdentical
        }
    }
}
