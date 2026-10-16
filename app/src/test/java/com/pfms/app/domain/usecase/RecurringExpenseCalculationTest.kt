package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Recurrence
import com.pfms.app.domain.model.RecurringExpense
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RecurringExpenseCalculationTest {

    @Test
    fun calculateNextDueDate_dailyRecurrence() {
        val start = LocalDate.of(2025, 5, 10)
        val next = RecurringExpenseCalculator.calculateNextDueDate(start, Recurrence.DAILY)
        assertEquals(LocalDate.of(2025, 5, 11), next)
    }

    @Test
    fun calculateNextDueDate_weeklyRecurrence() {
        val start = LocalDate.of(2025, 5, 10)
        val next = RecurringExpenseCalculator.calculateNextDueDate(start, Recurrence.WEEKLY)
        assertEquals(LocalDate.of(2025, 5, 17), next)
    }

    @Test
    fun calculateNextDueDate_monthlyRecurrence() {
        val start = LocalDate.of(2025, 5, 15)
        val next = RecurringExpenseCalculator.calculateNextDueDate(start, Recurrence.MONTHLY)
        assertEquals(LocalDate.of(2025, 6, 15), next)
    }

    @Test
    fun calculateNextDueDate_monthBoundaryHandling() {
        // Jan 31 plus 1 month -> Feb 28 (on non-leap year 2025)
        val jan31 = LocalDate.of(2025, 1, 31)
        val next = RecurringExpenseCalculator.calculateNextDueDate(jan31, Recurrence.MONTHLY)
        assertEquals(LocalDate.of(2025, 2, 28), next)

        // March 31 plus 1 month -> April 30
        val mar31 = LocalDate.of(2025, 3, 31)
        val aprNext = RecurringExpenseCalculator.calculateNextDueDate(mar31, Recurrence.MONTHLY)
        assertEquals(LocalDate.of(2025, 4, 30), aprNext)
    }

    @Test
    fun calculateNextDueDate_leapYearHandling() {
        // 2024 is a leap year; Jan 31 plus 1 month -> Feb 29
        val leapJan31 = LocalDate.of(2024, 1, 31)
        val leapNext = RecurringExpenseCalculator.calculateNextDueDate(leapJan31, Recurrence.MONTHLY)
        assertEquals(LocalDate.of(2024, 2, 29), leapNext)

        // Feb 29, 2024 plus 1 year / 1 month boundary
        val leapFeb29 = LocalDate.of(2024, 2, 29)
        val marNext = RecurringExpenseCalculator.calculateNextDueDate(leapFeb29, Recurrence.MONTHLY)
        assertEquals(LocalDate.of(2024, 3, 29), marNext)
    }

    @Test
    fun isDue_accuratelyIdentifiesOverdueAndUpcoming() {
        val today = LocalDate.of(2025, 6, 15)

        val pastDue = RecurringExpense(
            id = "t1",
            title = "Wifi",
            nextDueDate = "2025-06-10",
            active = true
        )
        assertTrue("Past due item must be due", RecurringExpenseCalculator.isDue(pastDue, today))

        val dueToday = RecurringExpense(
            id = "t2",
            title = "Electricity",
            nextDueDate = "2025-06-15",
            active = true
        )
        assertTrue("Item due today must be due", RecurringExpenseCalculator.isDue(dueToday, today))

        val future = RecurringExpense(
            id = "t3",
            title = "Rent",
            nextDueDate = "2025-06-20",
            active = true
        )
        assertFalse("Future item must not be due", RecurringExpenseCalculator.isDue(future, today))

        val paused = RecurringExpense(
            id = "t4",
            title = "Gym",
            nextDueDate = "2025-06-10",
            active = false
        )
        assertFalse("Paused item must never be flagged as due", RecurringExpenseCalculator.isDue(paused, today))
    }
}
