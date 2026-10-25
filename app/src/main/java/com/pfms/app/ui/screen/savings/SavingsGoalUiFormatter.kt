package com.pfms.app.ui.screen.savings

import com.pfms.app.domain.model.savings.DiscretionarySpendingImpact
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.domain.usecase.savings.SavingsGoalEntryRules
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display formatting. Compatibility validation methods delegate to domain rules. */
object SavingsGoalUiFormatter {
    fun formatLkr(amountMinor: Long, showSign: Boolean = false): String {
        val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }.format(BigDecimal.valueOf(amountMinor, 2).abs())
        return (if (amountMinor < 0) "- LKR " else if (showSign) "+ LKR " else "LKR ") + formatted
    }
    fun formatMinorToDecimalString(amountMinor: Long): String = BigDecimal.valueOf(amountMinor, 2).toPlainString()
    fun parseLkrToMinor(input: String): Long? = SavingsGoalEntryRules.parseAmount(input)
    fun formatDisplayDate(dateIso: String?): String {
        if (dateIso.isNullOrBlank()) return "N/A"
        return runCatching { LocalDate.parse(dateIso.trim()).format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)) }.getOrDefault(dateIso)
    }
    fun formatDiscretionaryImpactSentence(impact: DiscretionarySpendingImpact?, isCompleted: Boolean): String {
        if (impact == null) return "No discretionary impact data available for this month."
        if (isCompleted || impact.requiredMonthlySavingMinor <= 0) return "Goal target reached — no monthly savings required this month."
        val percentage = NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(SafeCalculations.finiteOrZero(impact.percentageOfRequiredSaving))
        return "Your discretionary spending this month equals $percentage% of one month's required saving."
    }
    data class GoalFormValidationResult(val isValid: Boolean, val nameError: String? = null, val targetAmountError: String? = null,
        val targetDateError: String? = null, val openingSavedError: String? = null, val targetAmountMinor: Long = 0, val openingSavedAmountMinor: Long = 0)
    fun validateGoalForm(name: String, targetAmountInput: String, targetDateInput: String, openingSavedInput: String): GoalFormValidationResult {
        val result = SavingsGoalEntryRules.validateGoal(name, targetAmountInput, targetDateInput, openingSavedInput)
        return GoalFormValidationResult(result.isValid, result.nameError, result.targetAmountError, result.targetDateError,
            result.openingSavedError, result.targetAmountMinor, result.openingSavedAmountMinor)
    }
    data class TransactionFormValidationResult(val isValid: Boolean, val amountError: String? = null, val dateError: String? = null, val amountMinor: Long = 0)
    fun validateTransactionForm(amountInput: String, dateInput: String): TransactionFormValidationResult {
        val result = SavingsGoalEntryRules.validateTransaction(amountInput, dateInput)
        return TransactionFormValidationResult(result.isValid, result.amountError, result.dateError, result.amountMinor)
    }
}
