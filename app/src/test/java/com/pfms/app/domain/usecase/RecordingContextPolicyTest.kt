package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
import com.pfms.app.domain.usecase.dashboard.TransactionHistoryCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecordingContextPolicyTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val past = YearMonth.of(2026, 9)
    private fun recordedAt(daysAgo: Long) = today.minusDays(daysAgo).atTime(23, 59).atZone(AppClock.zone).toInstant().toEpochMilli()
    private fun income(id: String = "income", date: String = "2026-09-01") = Income(id, "a", 100000, receivedDate = date)
    private fun records(incomes: List<Income> = listOf(income()), expenses: List<Expense> = emptyList()) =
        MonthlyFinancialRecords("a", past, incomes, expenses)

    @Test fun validPastDatesIncludePreviousMonthsYearsAndLeapDays() {
        for (date in listOf("2026-09-01", "2025-01-31", "2024-02-29", "1900-01-01")) {
            assertNotNull(FinancialEntryDate.parse(date))
            assertNull(ExpenseRules.validateExpense(100, "food", date))
        }
    }

    @Test fun malformedAndImpossibleDatesAreRejected() {
        for (date in listOf("", "2025-02-29", "2026-02-30", "2026-13-01", "2026-9-1", "2026-09-01T12:00:00"))
            assertNull(date, FinancialEntryDate.parse(date))
    }

    @Test fun plannedFutureEntriesRemainSupported() { assertNotNull(FinancialEntryDate.parse("2027-01-01")) }
    @Test fun newUserAndUnknownLegacyActivityDoNotTriggerWelcome() {
        assertFalse(WelcomeBackEligibility.isEligible(null, today))
        assertFalse(WelcomeBackEligibility.isEligible(0, today))
    }
    @Test fun sixCalendarDaysDoesNotTriggerWelcome() { assertFalse(WelcomeBackEligibility.isEligible(recordedAt(6), today)) }
    @Test fun exactlySevenCalendarDaysTriggersWelcome() { assertTrue(WelcomeBackEligibility.isEligible(recordedAt(7), today)) }
    @Test fun eightOrMoreCalendarDaysTriggersWelcome() {
        for (days in listOf(8L, 30L, 365L)) assertTrue(WelcomeBackEligibility.isEligible(recordedAt(days), today))
    }
    @Test fun calendarDaysAreUsedRatherThanElapsedHours() {
        // Only six days and one minute have elapsed, but the local dates are seven days apart.
        val logged = LocalDate.of(2026, 10, 13).atTime(23, 59).atZone(AppClock.zone).toInstant().toEpochMilli()
        assertTrue(WelcomeBackEligibility.isEligible(logged, LocalDate.of(2026, 10, 20)))
    }
    @Test fun aBackdatedEntryLoggedTodayResetsInactivity() {
        val entry = income(date = "2020-01-01").copy(createdAt = recordedAt(0), updatedAt = recordedAt(0))
        assertFalse(WelcomeBackEligibility.isEligible(entry.createdAt, today))
    }
    @Test fun futureClockSkewDoesNotProduceAWelcomePrompt() { assertFalse(WelcomeBackEligibility.isEligible(recordedAt(-1), today)) }
    @Test fun emptyPastMonthIsUnconfirmedWithoutInventingActivity() {
        val state = MonthlyDataPolicy.assess(records(emptyList()), today)
        assertEquals(MonthlyDataReason.NO_ENTRIES, state.reason)
        assertTrue(state.isPartial)
        assertEquals(0, state.recordCount)
    }
    @Test fun emptyMonthCanBeExplicitlyConfirmedAsNoActivity() {
        val empty = records(emptyList())
        val signature = MonthlyDataPolicy.assess(empty, today).signature
        val reviewed = MonthlyDataPolicy.assess(empty.copy(reviewedSignature = signature), today)
        assertEquals(MonthlyDataReason.REVIEWED, reviewed.reason)
        assertFalse(reviewed.isPartial)
        assertEquals(0, reviewed.recordCount)
    }
    @Test fun sparseEntryDaysAndIncomeOnlyMonthsAreNotAssumedCompleteOrMissing() {
        val sparse = records(listOf(income("one"), income("two", "2026-09-30")))
        val assessment = MonthlyDataPolicy.assess(sparse, today)
        assertEquals(MonthlyDataReason.UNREVIEWED, assessment.reason)
        assertTrue(assessment.canReview)
        assertEquals(MonthlyDataReason.REVIEWED,
            MonthlyDataPolicy.assess(sparse.copy(reviewedSignature = assessment.signature), today).reason)
    }
    @Test fun openMonthsRemainPartialEvenWithAReviewSignature() {
        val current = records().copy(month = YearMonth.from(today))
        val signature = MonthlyDataPolicy.assess(current, today).signature
        val state = MonthlyDataPolicy.assess(current.copy(reviewedSignature = signature), today)
        assertEquals(MonthlyDataReason.MONTH_IN_PROGRESS, state.reason)
        assertFalse(state.canReview)
    }
    @Test fun missingRealizedCurrencyConversionIsKnownIncomplete() {
        val state = MonthlyDataPolicy.assess(records(listOf(income().copy(currency = "USD"))), today)
        assertEquals(MonthlyDataReason.MISSING_CONVERSION, state.reason)
        assertFalse(state.canReview)
    }
    @Test fun pendingInvoiceDoesNotInventMissingRealizedIncome() {
        val state = MonthlyDataPolicy.assess(records(listOf(income().copy(source = "Freelance", currency = "USD", paymentStatus = "Expected"))), today)
        assertEquals(MonthlyDataReason.UNREVIEWED, state.reason)
    }
    @Test fun invalidUnassignableDatesPreventAFalseCompletenessClaim() {
        val state = MonthlyDataPolicy.assess(records(listOf(income(date = ""))), today)
        assertEquals(MonthlyDataReason.INVALID_RECORD, state.reason)
        assertFalse(state.canReview)
    }
    @Test fun syncAcknowledgementsAndOrderingDoNotInvalidateReview() {
        val entries = listOf(income("a"), income("b"))
        val before = MonthlyDataPolicy.assess(records(entries), today)
        val synced = records(entries.reversed().map { it.copy(createdAt = 100, updatedAt = 200) }).copy(reviewedSignature = before.signature)
        assertEquals(MonthlyDataReason.REVIEWED, MonthlyDataPolicy.assess(synced, today).reason)
    }
    @Test fun editsAdditionsAndDeletionsInvalidateReview() {
        val original = records()
        val signature = MonthlyDataPolicy.assess(original, today).signature
        for (changed in listOf(original.copy(incomes = listOf(income().copy(amountMinor = 200000))),
            original.copy(incomes = original.incomes + income("new")), original.copy(incomes = emptyList()))) {
            assertTrue(MonthlyDataPolicy.assess(changed.copy(reviewedSignature = signature), today).isPartial)
        }
    }
    @Test fun backdatedRecordsAndDateEditsUseTheTransactionMonthForTotalsAndHistory() {
        val oldExpense = Expense(id = "old", amountMinor = 25000, date = "2025-09-20", createdAt = recordedAt(0))
        val historicalIncome = income().copy(createdAt = recordedAt(0))
        val september = MonthlyFinanceCalculator.summarize(listOf(historicalIncome), listOf(oldExpense), past)
        assertEquals(100000L, september.totalIncomeMinor)
        assertEquals(0L, september.totalExpenseMinor)
        assertEquals(25000L, MonthlyFinanceCalculator.summarize(emptyList(), listOf(oldExpense), past.minusYears(1)).totalExpenseMinor)
        val edited = historicalIncome.copy(receivedDate = "2026-08-31")
        assertEquals(0L, MonthlyFinanceCalculator.summarize(listOf(edited), emptyList(), past).totalIncomeMinor)
        assertEquals(100000L, MonthlyFinanceCalculator.summarize(listOf(edited), emptyList(), past.minusMonths(1)).totalIncomeMinor)
        assertEquals("2026-08-31", TransactionHistoryCalculator.merge(listOf(edited), emptyList()).single().date)
    }
}
