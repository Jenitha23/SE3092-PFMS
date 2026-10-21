package com.pfms.app.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class ReminderDestinationTest {
    @Test fun onlyReminderScreensAreAcceptedFromIntents() {
        assertEquals(Routes.Subscriptions, Routes.reminderDestination(Routes.Subscriptions))
        assertEquals(Routes.RecurringExpenses, Routes.reminderDestination(Routes.RecurringExpenses))
        assertNull(Routes.reminderDestination(null))
        assertNull(Routes.reminderDestination(Routes.Settings))
        assertNull(Routes.reminderDestination("unknown"))
    }
    @Test fun allFinancialReminderDestinationsAreAllowlistedWithoutAcceptingArbitraryRoutes() {
        for (route in listOf(Routes.Dashboard, Routes.IncomeList, Routes.SpendingAnalytics, Routes.SavingsGoal,
            Routes.RecurringExpenses, Routes.Subscriptions)) assertEquals(route, Routes.reminderDestination(route))
        assertNull(Routes.reminderDestination("edit_income/private-id"))
    }
    @Test fun aNotificationFromAnotherAccountIsNotConsumedAsThatUsersReminder() {
        assertTrue(Routes.reminderMatchesUser("a", "a"))
        assertFalse(Routes.reminderMatchesUser("a", "b"))
        assertFalse(Routes.reminderMatchesUser("a", null))
    }
}
