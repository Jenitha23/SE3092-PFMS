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
}
