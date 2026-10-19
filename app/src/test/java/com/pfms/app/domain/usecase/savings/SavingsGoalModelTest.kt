
package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.SavingsGoalDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsGoalModelTest {

    @Test
    fun `prefilled example values match FR-23 requirements`() {
        val refDate = LocalDate.of(2026, 10, 16)
        val example = SavingsGoalDefaults.createPrefilledExample(refDate, userId = "user_123")

        // 1. Name: MacBook Pro M4
        assertEquals("MacBook Pro M4", example.name)
        // 2. Target: LKR 490,000.00 = 49,000,000 minor units
        assertEquals(49_000_000L, example.targetAmountMinor)
        // 3. Opening: LKR 11,200.00 = 1,120,000 minor units
        assertEquals(1_120_000L, example.openingSavedAmountMinor)
        // 4. Status: ACTIVE
        assertEquals(GoalStatus.ACTIVE, example.status)
        assertEquals("user_123", example.userId)
    }

    @Test
    fun `prefilled example default target date is exactly 12 months ahead`() {
        val refDate = LocalDate.of(2026, 10, 16)
        val example = SavingsGoalDefaults.createPrefilledExample(refDate)

        assertEquals("2027-10-16", example.targetDate)
        assertEquals(12L, SavingsGoalDefaults.EXAMPLE_TARGET_MONTHS_AHEAD)
    }

    @Test
    fun `supplied userId is preserved in prefilled example`() {
        val testUserId = "user_savings_test_999"
        val example = SavingsGoalDefaults.createPrefilledExample(userId = testUserId)

        assertEquals(testUserId, example.userId)
    }
}
