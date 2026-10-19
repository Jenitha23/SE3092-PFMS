package com.pfms.app.domain.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * Domain model representing a user's savings goal (FR-23, FR-28).
 *
 * Money values are represented in integer minor units (e.g. cents in LKR, where 100 minor units = 1 LKR).
 */
data class SavingsGoal(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetAmountMinor: Long = 0L,
    val targetDate: String = "", // ISO format: YYYY-MM-DD
    val openingSavedAmountMinor: Long = 0L,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/**
 * Status lifecycle of a savings goal.
 */
enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED
}

/**
 * Factory defaults for the pre-filled example goal specified in FR-23.
 */
object SavingsGoalDefaults {
    const val EXAMPLE_NAME = "MacBook Pro M4"

    // LKR 490,000.00 = 49,000,000 minor units
    const val EXAMPLE_TARGET_AMOUNT_MINOR = 49_000_000L

    // LKR 11,200.00 = 1,120,000 minor units
    const val EXAMPLE_OPENING_SAVED_MINOR = 1_120_000L

    const val EXAMPLE_TARGET_MONTHS_AHEAD = 12L

    /**
     * Creates an editable pre-filled savings goal example (FR-23) based on a reference date.
     */
    fun createPrefilledExample(
        referenceDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Colombo")),
        userId: String = ""
    ): SavingsGoal {
        return SavingsGoal(
            id = "",
            userId = userId,
            name = EXAMPLE_NAME,
            targetAmountMinor = EXAMPLE_TARGET_AMOUNT_MINOR,
            targetDate = referenceDate.plusMonths(EXAMPLE_TARGET_MONTHS_AHEAD).toString(),
            openingSavedAmountMinor = EXAMPLE_OPENING_SAVED_MINOR,
            status = GoalStatus.ACTIVE
        )
    }
}
