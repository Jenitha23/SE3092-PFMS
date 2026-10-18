package com.pfms.app.domain.model.dashboard

import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.PaymentMethods

enum class TransactionType { INCOME, EXPENSE }

/**
 * One row in the dashboard "latest transactions" list, the "top 5 expenses" list and the
 * transaction history (FR-29, FR-32, FR-33). Income and expenses are normalised into this shape
 * so the three screens render and filter them with the same code.
 *
 * All money is LKR minor units (integer), never floating point (NFR-16).
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    /** ISO date, yyyy-MM-dd. */
    val date: String,
    val createdAt: Long,
    /** Income source (Salary, Freelance...) or expense category name. */
    val title: String,
    /** Merchant / note / project, already joined for display. May be empty. */
    val subtitle: String,
    /** Absolute LKR amount. The sign shown in the UI comes from [type] and [isLoss]. */
    val amountMinor: Long,
    /**
     * Signed effect on net savings: income +, crypto loss -, expense -, and 0 for
     * unrealised income (freelance still marked "Expected", FR-09).
     */
    val netEffectMinor: Long,
    val incomeSource: String? = null,
    val expenseCategory: String? = null,
    val paymentMethod: ExpensePaymentMethod? = null,
    val classification: ExpenseClassification? = null,
    /** True for freelance income that is still "Expected" and not part of totals. */
    val isPending: Boolean = false,
    /** True for a crypto loss entry. */
    val isLoss: Boolean = false
)

/** Human label for a payment method, using the exact FR-15 wording. */
fun ExpensePaymentMethod.displayLabel(): String = when (this) {
    ExpensePaymentMethod.CASH -> PaymentMethods.CASH
    ExpensePaymentMethod.CARD -> PaymentMethods.CARD
    ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT -> PaymentMethods.BANK_TRANSFER
    ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM -> PaymentMethods.DIGITAL_WALLET
}

fun ExpenseClassification.displayLabel(): String = when (this) {
    ExpenseClassification.COMMITTED -> "Committed"
    ExpenseClassification.DISCRETIONARY -> "Discretionary"
}
