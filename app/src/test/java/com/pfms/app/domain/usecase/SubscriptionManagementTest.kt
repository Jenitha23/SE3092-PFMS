package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Recurrence
import com.pfms.app.domain.model.RecurringExpense
import com.pfms.app.domain.model.SubscriptionFlag
import org.junit.Assert.*
import org.junit.Test

class SubscriptionManagementTest {

    @Test
    fun monthlySubscriptionTotal_calculatesExactMonthlyAmounts() {
        val subscriptions = listOf(
            RecurringExpense(
                id = "s1",
                title = "Netflix",
                amountMinor = 300000L, // 3000.00 LKR
                recurrence = Recurrence.MONTHLY,
                subscription = true,
                active = true
            ),
            RecurringExpense(
                id = "s2",
                title = "Spotify",
                amountMinor = 150000L, // 1500.00 LKR
                recurrence = Recurrence.MONTHLY,
                subscription = true,
                active = true
            )
        )

        val total = RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(subscriptions)
        assertEquals(450000L, total) // 4500.00 LKR
    }

    @Test
    fun monthlySubscriptionTotal_convertsWeeklyUsing52DividedBy12() {
        // 1200.00 LKR / week -> 120000 minor units
        // 120000 * 52 / 12 = 520000 minor units (5200.00 LKR)
        val weeklySub = RecurringExpense(
            id = "w1",
            title = "Meal Kit Weekly",
            amountMinor = 120000L,
            recurrence = Recurrence.WEEKLY,
            subscription = true,
            active = true
        )

        val total = RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(listOf(weeklySub))
        assertEquals(520000L, total)
    }

    @Test
    fun monthlySubscriptionTotal_convertsDailyUsing365DividedBy12() {
        // 100.00 LKR / day -> 10000 minor units
        // 10000 * 365 / 12 = 3650000 / 12 = 304166.666... -> rounded HALF_UP = 304167 minor units
        val dailySub = RecurringExpense(
            id = "d1",
            title = "Newspaper Daily",
            amountMinor = 10000L,
            recurrence = Recurrence.DAILY,
            subscription = true,
            active = true
        )

        val total = RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(listOf(dailySub))
        assertEquals(304167L, total)
    }

    @Test
    fun monthlySubscriptionTotal_excludesCancelledSubscriptions() {
        val activeSub = RecurringExpense(
            id = "s1",
            title = "Active Monthly",
            amountMinor = 200000L,
            recurrence = Recurrence.MONTHLY,
            subscription = true,
            active = true
        )

        val cancelledSub = RecurringExpense(
            id = "s2",
            title = "Cancelled Sub",
            amountMinor = 500000L,
            recurrence = Recurrence.MONTHLY,
            subscription = true,
            active = false,
            flag = SubscriptionFlag.CANCEL
        )

        val total = RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(listOf(activeSub, cancelledSub))
        assertEquals("Cancelled subscriptions must be excluded from monthly cost", 200000L, total)
    }

    @Test
    fun monthlySubscriptionTotal_excludesNonSubscriptionRecurringExpenses() {
        val subscription = RecurringExpense(
            id = "s1",
            title = "Cloud Storage",
            amountMinor = 50000L,
            recurrence = Recurrence.MONTHLY,
            subscription = true,
            active = true
        )

        val recurringExpense = RecurringExpense(
            id = "r1",
            title = "Apartment Rent",
            amountMinor = 8500000L,
            recurrence = Recurrence.MONTHLY,
            subscription = false, // Not a subscription
            active = true
        )

        val total = RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(listOf(subscription, recurringExpense))
        assertEquals("Only subscriptions must be included in subscription total", 50000L, total)
    }
}
