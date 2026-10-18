package com.pfms.app.domain.model.dashboard

/**
 * Read-only view of the user's single active savings goal, for the dashboard card (FR-29).
 * Goal creation and the contribution ledger (FR-23..28) live in the savings-goal feature; this
 * model only carries what the card shows.
 *
 * saved = opening + contributions - withdrawals (FR-24). Derived values never go negative or
 * above 100% so a completed or overdue goal cannot show invalid numbers (FR-25).
 */
data class ActiveGoalSnapshot(
    val id: String,
    val name: String,
    val targetAmountMinor: Long,
    val savedAmountMinor: Long,
    /** ISO date or null. */
    val targetDate: String?
) {
    val remainingMinor: Long get() = (targetAmountMinor - savedAmountMinor).coerceAtLeast(0L)

    val progressPercent: Int
        get() = if (targetAmountMinor <= 0L) 0
        else ((savedAmountMinor.coerceAtLeast(0L) * 100L) / targetAmountMinor).coerceIn(0L, 100L).toInt()

    val isCompleted: Boolean get() = targetAmountMinor > 0L && savedAmountMinor >= targetAmountMinor
}
