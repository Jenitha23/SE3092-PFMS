package com.pfms.app.domain.model.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveGoalSnapshotTest {

    private fun goal(target: Long, saved: Long) = ActiveGoalSnapshot("g", "MacBook Pro M4", target, saved, "2027-10-01")

    @Test
    fun progressAndRemainingForAnInProgressGoal() {
        val g = goal(49_000_000L, 11_200_000L)
        assertEquals(22, g.progressPercent)
        assertEquals(37_800_000L, g.remainingMinor)
        assertFalse(g.isCompleted)
    }

    @Test
    fun overSavedGoalIsCompletedAtExactly100AndNeverShowsNegativeRemaining() {
        val g = goal(1_000_000L, 1_500_000L)
        assertEquals(100, g.progressPercent)
        assertEquals(0L, g.remainingMinor)
        assertTrue(g.isCompleted)
    }

    @Test
    fun negativeSavedAmountNeverShowsNegativeProgress() {
        val g = goal(1_000_000L, -50_000L)
        assertEquals(0, g.progressPercent)
    }

    @Test
    fun zeroTargetDoesNotDivideByZero() {
        val g = goal(0L, 0L)
        assertEquals(0, g.progressPercent)
        assertFalse(g.isCompleted)
    }
}
