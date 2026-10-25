package com.pfms.app.domain.usecase.savings

import java.math.BigDecimal
import java.time.LocalDate

/** Exact minor-unit parsing and input rules shared by savings forms. */
object SavingsGoalEntryRules {
    data class GoalResult(val isValid: Boolean, val nameError: String? = null, val targetAmountError: String? = null,
        val targetDateError: String? = null, val openingSavedError: String? = null,
        val targetAmountMinor: Long = 0, val openingSavedAmountMinor: Long = 0)
    data class TransactionResult(val isValid: Boolean, val amountError: String? = null, val dateError: String? = null, val amountMinor: Long = 0)

    fun parseAmount(input: String): Long? = runCatching {
        val value = BigDecimal(input.trim().replace(",", ""))
        if (value < BigDecimal.ZERO) null else value.movePointRight(2).longValueExact()
    }.getOrNull()

    fun validateGoal(name: String, target: String, date: String, opening: String): GoalResult {
        val amount = parseAmount(target)
        val saved = if (opening.isBlank()) 0L else parseAmount(opening)
        val nameError = if (name.isBlank()) "Goal name cannot be empty" else null
        val amountError = if (amount == null || amount <= 0) "Target amount must be greater than zero" else null
        val dateError = dateError(date, "Target date is required")
        val savedError = if (saved == null) "Opening saved amount cannot be negative" else null
        return GoalResult(nameError == null && amountError == null && dateError == null && savedError == null,
            nameError, amountError, dateError, savedError, amount ?: 0, saved ?: 0)
    }

    fun validateTransaction(amountInput: String, date: String): TransactionResult {
        val amount = parseAmount(amountInput)
        val amountError = if (amount == null || amount <= 0) "Transaction amount must be greater than zero" else null
        val dateError = dateError(date, "Date is required")
        return TransactionResult(amountError == null && dateError == null, amountError, dateError, amount ?: 0)
    }

    private fun dateError(date: String, blankMessage: String): String? = when {
        date.isBlank() -> blankMessage
        runCatching { LocalDate.parse(date.trim()) }.isFailure -> "Invalid date format (must be YYYY-MM-DD)"
        else -> null
    }
}
