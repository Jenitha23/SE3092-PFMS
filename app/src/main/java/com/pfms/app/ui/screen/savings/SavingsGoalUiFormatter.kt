package com.pfms.app.ui.screen.savings

import com.pfms.app.domain.model.savings.DiscretionarySpendingImpact
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * UI formatting and validation helpers for the Savings Goal feature.
 */
object SavingsGoalUiFormatter {

    private val numberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val shortNumberFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)

    /**
     * Formats integer minor units into standard LKR display (e.g. "LKR 490,000.00").
     */
    fun formatLkr(amountMinor: Long, showSign: Boolean = false): String {
        val isNegative = amountMinor < 0L
        val absAmount = BigDecimal.valueOf(Math.abs(amountMinor), 2)
        val formatted = numberFormat.format(absAmount)
        val prefix = when {
            isNegative -> "- LKR "
            showSign -> "+ LKR "
            else -> "LKR "
        }
        return "$prefix$formatted"
    }

    /**
     * Formats integer minor units without the "LKR " prefix for edit fields (e.g. "490000.00").
     */
    fun formatMinorToDecimalString(amountMinor: Long): String {
        return BigDecimal.valueOf(amountMinor, 2).toPlainString()
    }

    /**
     * Parses a decimal or integer string input (e.g. "490,000" or "490000.50") into integer minor units (Long).
     * Returns null if the input is blank, negative, or invalid.
     */
    fun parseLkrToMinor(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isBlank()) return null
        return try {
            val decimal = BigDecimal(cleaned)
            if (decimal < BigDecimal.ZERO) null
            else decimal.movePointRight(2).toLong()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats ISO date string ("2027-10-17") to display format ("Oct 17, 2027").
     */
    fun formatDisplayDate(dateIso: String?): String {
        if (dateIso.isNullOrBlank()) return "N/A"
        return try {
            val parsed = LocalDate.parse(dateIso.trim())
            parsed.format(displayDateFormatter)
        } catch (e: DateTimeParseException) {
            dateIso
        }
    }

    /**
     * Builds the formatted discretionary spending impact explanation sentence (FR-27).
     */
    fun formatDiscretionaryImpactSentence(
        impact: DiscretionarySpendingImpact?,
        isCompleted: Boolean
    ): String {
        if (impact == null) {
            return "No discretionary impact data available for this month."
        }
        if (isCompleted || impact.requiredMonthlySavingMinor <= 0L) {
            return "Goal target reached — no monthly savings required this month."
        }
        val pct = shortNumberFormat.format(impact.percentageOfRequiredSaving)
        return "Your discretionary spending this month equals $pct% of one month's required saving."
    }

    /**
     * Validates goal creation / edit form inputs.
     */
    data class GoalFormValidationResult(
        val isValid: Boolean,
        val nameError: String? = null,
        val targetAmountError: String? = null,
        val targetDateError: String? = null,
        val openingSavedError: String? = null,
        val targetAmountMinor: Long = 0L,
        val openingSavedAmountMinor: Long = 0L
    )

    fun validateGoalForm(
        name: String,
        targetAmountInput: String,
        targetDateInput: String,
        openingSavedInput: String
    ): GoalFormValidationResult {
        var isValid = true
        var nameError: String? = null
        var targetAmountError: String? = null
        var targetDateError: String? = null
        var openingSavedError: String? = null

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            nameError = "Goal name cannot be empty"
            isValid = false
        }

        val targetMinor = parseLkrToMinor(targetAmountInput)
        if (targetMinor == null || targetMinor <= 0L) {
            targetAmountError = "Target amount must be greater than zero"
            isValid = false
        }

        val trimmedDate = targetDateInput.trim()
        if (trimmedDate.isBlank()) {
            targetDateError = "Target date is required"
            isValid = false
        } else {
            try {
                LocalDate.parse(trimmedDate)
            } catch (e: DateTimeParseException) {
                targetDateError = "Invalid date format (must be YYYY-MM-DD)"
                isValid = false
            }
        }

        val openingSavedMinor = if (openingSavedInput.isBlank()) 0L else parseLkrToMinor(openingSavedInput)
        if (openingSavedMinor == null || openingSavedMinor < 0L) {
            openingSavedError = "Opening saved amount cannot be negative"
            isValid = false
        }

        return GoalFormValidationResult(
            isValid = isValid,
            nameError = nameError,
            targetAmountError = targetAmountError,
            targetDateError = targetDateError,
            openingSavedError = openingSavedError,
            targetAmountMinor = targetMinor ?: 0L,
            openingSavedAmountMinor = openingSavedMinor ?: 0L
        )
    }

    /**
     * Validates transaction creation / edit inputs.
     */
    data class TransactionFormValidationResult(
        val isValid: Boolean,
        val amountError: String? = null,
        val dateError: String? = null,
        val amountMinor: Long = 0L
    )

    fun validateTransactionForm(
        amountInput: String,
        dateInput: String
    ): TransactionFormValidationResult {
        var isValid = true
        var amountError: String? = null
        var dateError: String? = null

        val amountMinor = parseLkrToMinor(amountInput)
        if (amountMinor == null || amountMinor <= 0L) {
            amountError = "Transaction amount must be greater than zero"
            isValid = false
        }

        val trimmedDate = dateInput.trim()
        if (trimmedDate.isBlank()) {
            dateError = "Date is required"
            isValid = false
        } else {
            try {
                LocalDate.parse(trimmedDate)
            } catch (e: DateTimeParseException) {
                dateError = "Invalid date format (must be YYYY-MM-DD)"
                isValid = false
            }
        }

        return TransactionFormValidationResult(
            isValid = isValid,
            amountError = amountError,
            dateError = dateError,
            amountMinor = amountMinor ?: 0L
        )
    }
}
