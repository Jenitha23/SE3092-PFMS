package com.pfms.app.viewmodel

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class RecurringScheduleOverviewTest {
    @Test fun monthlyEstimateUsesActiveRecurringSchedulesAndExactFrequencyConversion() {
        val monthly = RecurringExpense(id = "monthly", amountMinor = 100000)
        val weekly = monthly.copy(id = "weekly", amountMinor = 30000, recurrence = Recurrence.WEEKLY)
        val daily = monthly.copy(id = "daily", amountMinor = 10000, recurrence = Recurrence.DAILY)
        assertEquals(534167L, RecurringExpenseCalculator.calculateMonthlyScheduledTotal(listOf(monthly, weekly, daily,
            monthly.copy(id = "paused", active = false), monthly.copy(id = "subscription", subscription = true))))
    }
    @Test fun dueSoonIncludesOverduePaymentsAndTheSevenDayBoundary() {
        val today = LocalDate.of(2026, 10, 24)
        val template = RecurringExpense(active = true, nextDueDate = today.plusDays(7).toString())
        assertTrue(RecurringExpenseCalculator.isDueSoon(template, today))
        assertTrue(RecurringExpenseCalculator.isDueSoon(template.copy(nextDueDate = today.minusDays(1).toString()), today))
        assertFalse(RecurringExpenseCalculator.isDueSoon(template.copy(nextDueDate = today.plusDays(8).toString()), today))
        assertFalse(RecurringExpenseCalculator.isDueSoon(template.copy(active = false), today))
        assertFalse(RecurringExpenseCalculator.isDueSoon(template.copy(subscription = true, flag = SubscriptionFlag.CANCEL), today))
    }
    @Test fun filteringKeepsTheUnfilteredMonthlyTotalAndCounts() {
        val date = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE)
        val rent = RecurringExpense("rent", "Monthly rent", "rent", "Rent", amountMinor = 2500000, nextDueDate = date.plusDays(30).toString())
        val utility = rent.copy(id = "wifi", title = "Wi-Fi", categoryId = "utilities", categoryName = "Utilities", amountMinor = 185000, nextDueDate = date.toString())
        val base = advancedExpenseScreenState(emptyList(), listOf(rent, utility), 0, AdvancedExpenseControls())
        val filtered = advancedExpenseScreenState(emptyList(), listOf(rent, utility), 0, AdvancedExpenseControls(recurringFilter = "due"))
        assertEquals("LKR 26,850.00", filtered.recurringMonthlyTotal)
        assertEquals(base.recurringMonthlyTotal, filtered.recurringMonthlyTotal)
        assertEquals(listOf("wifi"), filtered.filteredRecurring.map { it.template.id })
        assertEquals(2, filtered.recurringFilters.first { it.key == "all" }.count)
        val category = advancedExpenseScreenState(emptyList(), listOf(rent, utility), 0, AdvancedExpenseControls(recurringFilter = "category:rent"))
        assertEquals(listOf("rent"), category.filteredRecurring.map { it.template.id })
    }
}
