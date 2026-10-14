
package com.pfms.app.domain.model

data class Expense(
    val id: String = "",
    val userId: String = "",

    // Money stored as integer minor units.
    val amountMinor: Long = 0L,
    val currency: String = "LKR",

    // Expense category and classification.
    val categoryId: String = "",
    val categoryName: String = "",
    val classification: ExpenseClassification =
        ExpenseClassification.DISCRETIONARY,

    // Payment details.
    val paymentMethod: ExpensePaymentMethod =
        ExpensePaymentMethod.CASH,
    val date: String = "",

    // Optional information.
    val merchant: String = "",
    val note: String = "",

    // Timestamps for duplicate detection and sync.
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

enum class ExpenseClassification {
    COMMITTED,
    DISCRETIONARY
}

enum class ExpensePaymentMethod {
    CASH,
    CARD,
    BANK_TRANSFER_AUTO_DEBIT,
    DIGITAL_WALLET_PLATFORM
}
