package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.savings.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialReminderUseCasesTest {
    private val progress = CalculateGoalProgressUseCase(CalculateSavedAmountUseCase())
    private val net = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
    private val rules = FinancialReminderUseCases(SalaryReminderUseCase(), CalculateGoalPaceUseCase(progress,
        CalculateCurrentMonthGoalContributionUseCase(), net, CalculateTrailingNetSavingsAverageUseCase(net)), progress)
    private val today = LocalDate.of(2026, 10, 20)
    private fun records(incomes: List<Income> = emptyList(), expenses: List<Expense> = emptyList()) = ReminderFinancialRecords("a", incomes, expenses)
    private fun invoice(date: String = "2026-10-05", status: String = "Expected") = Income("invoice", "a", 10000,
        source = "Freelance", receivedDate = date, paymentStatus = status, projectName = "Client")
    private fun goal() = SavingsGoal("goal", "a", "Laptop", 1200000, "2027-10-20")

    @Test fun dailyLogIsANeutralActionableReminder() {
        assertEquals(ReminderDestination.DAILY_ENTRY, rules.daily().single().destination)
        assertTrue(rules.daily().single().body.contains("income or expenses"))
    }
    @Test fun salaryUsesTheExistingPaydayAndExcludesEarlyDisabledAndConfirmedMonths() {
        val base = records().copy(salary = SalaryTemplate("salary", "a", 10000, paymentDay = 20))
        assertTrue(rules.salary(base, today.minusDays(1)).isEmpty())
        assertEquals("2026-10", rules.salary(base, today).single().occurrence)
        assertEquals("2026-10", rules.salary(base, today.plusDays(1)).single().occurrence)
        assertTrue(rules.salary(base.copy(salary = base.salary!!.copy(lastConfirmedMonth = "2026-10")), today).isEmpty())
        assertTrue(rules.salary(base.copy(salary = base.salary!!.copy(isEnabled = false)), today).isEmpty())
    }
    @Test fun salaryDay31ClampsToTheLastDayOfFebruary() {
        val base = records().copy(salary = SalaryTemplate("salary", "a", 10000, paymentDay = 31))
        assertTrue(rules.salary(base, LocalDate.of(2026, 2, 27)).isEmpty())
        assertEquals("2026-02", rules.salary(base, LocalDate.of(2026, 2, 28)).single().occurrence)
        assertEquals("2024-02", rules.salary(base, LocalDate.of(2024, 2, 29)).single().occurrence)
    }
    @Test fun recurringHandlesMultipleItemsAndSkipsFutureInactiveAndCancelledTemplates() {
        val due = RecurringExpense("rent", "Rent", amountMinor = 10000, nextDueDate = "2026-10-20")
        val rows = listOf(due, due.copy(id = "power"), due.copy(id = "future", nextDueDate = "2026-10-21"),
            due.copy(id = "paused", active = false), due.copy(id = "cancel", subscription = true, flag = SubscriptionFlag.CANCEL))
        val messages = rules.recurring(records().copy(recurring = rows), today)
        assertEquals(2, messages.size); assertEquals(2, messages.map { it.key }.toSet().size)
        assertTrue(messages.all { it.occurrence == "2026-10-20" })
    }
    @Test fun confirmingAnOccurrenceAdvancesItsDateAndSuppressesTheOldReminder() {
        val due = RecurringExpense("rent", amountMinor = 100, nextDueDate = "2026-10-20")
        assertEquals(1, rules.recurring(records().copy(recurring = listOf(due)), today).size)
        assertTrue(rules.recurring(records().copy(recurring = listOf(due.copy(nextDueDate = "2026-11-20"))), today).isEmpty())
    }
    @Test fun invoiceAt14DaysIsNotOverdueBut15DaysIs() {
        assertTrue(rules.freelance(records(listOf(invoice("2026-10-06"))), today).isEmpty())
        assertEquals(1, rules.freelance(records(listOf(invoice("2026-10-05"))), today).size)
    }
    @Test fun invoiceUsesTheSelectedInvoiceDateInsteadOfCreatedOrUpdatedTimestamps() {
        val oldInvoiceLoggedToday = invoice().copy(createdAt = Long.MAX_VALUE, updatedAt = Long.MAX_VALUE)
        assertEquals("pending:2026-10-05", rules.freelance(records(listOf(oldInvoiceLoggedToday)), today).single().occurrence)
        assertTrue(rules.freelance(records(listOf(invoice("2026-10-20").copy(createdAt = 1))), today).isEmpty())
    }
    @Test fun paidCancelledInvalidFutureAndOtherUsersInvoicesAreExcluded() {
        for (row in listOf(invoice(status = "Received"), invoice(status = "Paid"), invoice(status = "Cancelled"),
            invoice("bad-date"), invoice("2026-10-21"), invoice().copy(userId = "b"), invoice().copy(source = "Salary")))
            assertTrue(rules.freelance(records(listOf(row)), today).isEmpty())
        assertEquals(1, rules.freelance(records(listOf(invoice(status = "Pending"))), today).size)
    }
    @Test fun unchangedPendingInvoiceKeepsItsOccurrenceAcrossDaysAndNoteEdits() {
        val before = rules.freelance(records(listOf(invoice())), today).single()
        val later = rules.freelance(records(listOf(invoice().copy(note = "Updated note", updatedAt = 999))), today.plusDays(1)).single()
        assertEquals(before.key, later.key); assertEquals(before.occurrence, later.occurrence)
    }
    @Test fun subscriptionReviewHonorsDateActiveStatusAndExplicitReviewFlag() {
        val sub = RecurringExpense("sub", "Streaming", subscription = true, reminderDate = "2026-10-20")
        fun messages(row: RecurringExpense) = rules.subscriptions(records().copy(recurring = listOf(row)), today)
        assertEquals(1, messages(sub).size)
        assertTrue(messages(sub.copy(reminderDate = "2026-10-21")).isEmpty())
        assertTrue(messages(sub.copy(active = false)).isEmpty())
        assertTrue(messages(sub.copy(flag = SubscriptionFlag.CANCEL)).isEmpty())
        assertTrue(messages(sub.copy(reminderDate = "")).isEmpty())
        assertEquals(1, messages(sub.copy(reminderDate = "", flag = SubscriptionFlag.REVIEW)).size)
    }
    @Test fun paymentDueAndSubscriptionReviewHaveIndependentEligibilityAndOccurrenceClaims() {
        val sub = RecurringExpense("sub", subscription = true, amountMinor = 100, nextDueDate = "2026-10-20", reminderDate = "2026-10-20")
        val snapshot = records().copy(recurring = listOf(sub))
        assertEquals(1, rules.recurring(snapshot, today).size)
        assertEquals(1, rules.subscriptions(snapshot, today).size)
        assertNotEquals(rules.recurring(snapshot, today).single().key, rules.subscriptions(snapshot, today).single().key)
    }
    @Test fun weeklySummaryUsesRecordedRealizedAmountsAndSelectedDatesWithinSevenCompleteDays() {
        val incomes = listOf(Income("salary", "a", 10000, receivedDate = "2026-10-13"),
            invoice("2026-10-14"), Income("usd", "a", 100, "USD", receivedDate = "2026-10-19", amountLkrMinor = 30000),
            Income("today", "a", 90000, receivedDate = "2026-10-20"), Income("old", "a", 90000, receivedDate = "2025-10-13"),
            Income("loss", "a", 1000, source = "Crypto", cryptoType = "Loss", receivedDate = "2026-10-17"))
        val expenses = listOf(Expense("expense", "a", 5000, date = "2026-10-18"))
        val message = rules.weekly(records(incomes, expenses), today).single()
        assertTrue(message.body.contains("income LKR 390.00"))
        assertTrue(message.body.contains("expenses LKR 50.00"))
        assertTrue(message.body.contains("net savings LKR 340.00"))
    }
    @Test fun emptyWeeklySummaryClearlyDescribesZeroRecordedActivity() {
        val body = rules.weekly(records(), today).single().body
        assertTrue(body.contains("No income or expenses recorded"))
        assertTrue(body.contains("net savings LKR 0.00"))
    }
    @Test fun weeklySummaryMarksMissingDatesAndConversionsWithoutInventingMoney() {
        val body = rules.weekly(records(listOf(Income("usd", "a", 100, "USD", receivedDate = "2026-10-15"),
            Income("bad", "a", 999999, receivedDate = ""))), today).single().body
        assertTrue(body.contains("Partial Data")); assertTrue(body.contains("income LKR 0.00"))
    }
    @Test fun weeklySummaryCanShowNegativeNetSavingsAndDoesNotOverflow() {
        val negative = rules.weekly(records(expenses = listOf(Expense("e", "a", 12345, date = "2026-10-15"))), today).single().body
        assertTrue(negative.contains("net savings LKR -123.45"))
        val huge = listOf(Income("a", "a", Long.MAX_VALUE, receivedDate = "2026-10-15"),
            Income("b", "a", Long.MAX_VALUE, receivedDate = "2026-10-15"))
        assertTrue(rules.weekly(records(huge), today).single().body.contains("184467440737095516.14"))
    }
    @Test fun behindAndOnTrackUseTheExistingTrailingThreeMonthPaceBenchmark() {
        val base = records().copy(goal = goal())
        assertNotNull(rules.goal(base, today).reminder)
        val income = listOf("2026-07-01", "2026-08-01", "2026-09-01").mapIndexed { n, date -> Income("i$n", "a", 200000, receivedDate = date) }
        assertNull(rules.goal(base.copy(incomes = income), today).reminder)
        assertEquals("on_track:goal", rules.goal(base.copy(incomes = income), today).state)
    }
    @Test fun completedInactiveInvalidAndOtherUsersGoalsNeverNotify() {
        for (goal in listOf(goal().copy(status = GoalStatus.COMPLETED), goal().copy(status = GoalStatus.ARCHIVED),
            goal().copy(openingSavedAmountMinor = 1200000), goal().copy(targetDate = ""), goal().copy(userId = "b")))
            assertNull(rules.goal(records().copy(goal = goal), today).reminder)
    }
    @Test fun unchangedBehindStateDoesNotChangeJustBecauseAnotherDayPasses() {
        val base = records().copy(goal = goal())
        assertEquals(rules.goal(base, today).state, rules.goal(base, today.plusDays(1)).state)
        assertNotEquals(rules.goal(base, today).state, rules.goal(base.copy(goal = goal().copy(targetAmountMinor = 1500000)), today).state)
    }
    @Test fun missingConversionAndExtremeValuesDoNotProduceUnreliableGoalAlerts() {
        val base = records(listOf(Income("x", "a", Long.MAX_VALUE, receivedDate = "2026-09-01"))).copy(goal = goal())
        assertNull(rules.goal(base, today).reminder)
        assertNull(rules.goal(base.copy(incomes = listOf(Income("x", "a", 100, "USD", receivedDate = "2026-09-01"))), today).reminder)
    }
}
