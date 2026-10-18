package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.dashboard.HistoryFilter
import com.pfms.app.domain.model.dashboard.TransactionTypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TransactionHistoryCalculatorTest {

    private val incomes = listOf(
        income("i1", "2026-10-25", 13_000_000L),
        income("i2", "2026-10-20", 500_000L, "Freelance", status = "Expected")
    )
    private val expenses = listOf(
        expense("e1", "2026-10-01", 3_400_000L, "Rent", ExpenseClassification.COMMITTED, ExpensePaymentMethod.CARD),
        expense("e2", "2026-10-20", 250_000L, "Coffee", createdAt = 5L),
        expense("e3", "2026-10-25", 150_000L, "Coffee", method = ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM, createdAt = 9L)
    )
    private val all = TransactionHistoryCalculator.merge(incomes, expenses)

    private fun ids(f: HistoryFilter) = TransactionHistoryCalculator.filter(all, f).map { it.id }

    @Test
    fun mergeIsNewestFirstThenByCreationTime() {
        assertEquals(listOf("e3", "i1", "e2", "i2", "e1"), all.map { it.id })
    }

    @Test
    fun noFilterReturnsEverything() {
        assertEquals(5, ids(HistoryFilter()).size)
    }

    @Test
    fun filtersByTransactionType() {
        assertEquals(listOf("e3", "e2", "e1"), ids(HistoryFilter(type = TransactionTypeFilter.EXPENSE)))
        assertEquals(listOf("i1", "i2"), ids(HistoryFilter(type = TransactionTypeFilter.INCOME)))
    }

    @Test
    fun filtersByInclusiveDateRange() {
        val f = HistoryFilter(startDate = LocalDate.of(2026, 10, 20), endDate = LocalDate.of(2026, 10, 25))
        assertEquals(listOf("e3", "i1", "e2", "i2"), ids(f))
    }

    @Test
    fun filtersByIncomeSourceAndNeverMatchesExpenses() {
        assertEquals(listOf("i2"), ids(HistoryFilter(incomeSource = "Freelance")))
    }

    @Test
    fun filtersByCategoryClassificationAndPaymentMethod() {
        assertEquals(listOf("e3", "e2"), ids(HistoryFilter(expenseCategory = "Coffee")))
        assertEquals(listOf("e1"), ids(HistoryFilter(classification = ExpenseClassification.COMMITTED)))
        assertEquals(listOf("e2"), ids(HistoryFilter(paymentMethod = ExpensePaymentMethod.CASH)))
    }

    @Test
    fun combinedFiltersMustAllMatch() {
        val f = HistoryFilter(expenseCategory = "Coffee", paymentMethod = ExpensePaymentMethod.CASH)
        assertEquals(listOf("e2"), ids(f))
    }

    @Test
    fun summaryCountsRowsAndExcludesExpectedFreelanceFromIncome() {
        val summary = TransactionHistoryCalculator.summarize(all)
        assertEquals(5, summary.count)
        assertEquals(13_000_000L, summary.incomeMinor)   // the 5,000 "Expected" invoice adds 0
        assertEquals(3_800_000L, summary.expenseMinor)
        assertEquals(9_200_000L, summary.netMinor)
    }

    @Test
    fun emptyResultSummarisesToZero() {
        val summary = TransactionHistoryCalculator.summarize(emptyList())
        assertEquals(0, summary.count)
        assertEquals(0L, summary.netMinor)
    }

    @Test
    fun expectedFreelanceIsMarkedPendingAndDoesNotAffectNet() {
        val pending = all.first { it.id == "i2" }
        assertTrue(pending.isPending)
        assertEquals(0L, pending.netEffectMinor)
    }
}
