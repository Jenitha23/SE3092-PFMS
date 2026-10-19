package com.pfms.app.domain.model

/**
 * Domain model representing a ledger transaction for a savings goal (FR-24).
 *
 * Money values are represented in integer minor units.
 * [amountMinor] is always non-negative; [transactionType] dictates whether it adds
 * to or subtracts from the savings goal.
 */
data class GoalTransaction(
    val id: String = "",
    val goalId: String = "",
    val userId: String = "",
    val amountMinor: Long = 0L,
    val transactionType: GoalTransactionType = GoalTransactionType.CONTRIBUTION,
    val date: String = "", // ISO format: YYYY-MM-DD
    val note: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    init {
        require(amountMinor >= 0L) {
            "Transaction amountMinor must be non-negative. Received: $amountMinor"
        }
    }
}

/**
 * Type of goal ledger transaction.
 */
enum class GoalTransactionType {
    CONTRIBUTION,
    WITHDRAWAL
}
