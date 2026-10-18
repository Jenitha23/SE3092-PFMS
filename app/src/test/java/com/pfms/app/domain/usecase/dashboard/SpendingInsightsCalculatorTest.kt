package com.pfms.app.domain.usecase.dashboard

import com.pfms.app.domain.model.ExpenseClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpendingInsightsCalculatorTest {

    private val rows = listOf(
        expense("a", "2026-10-01", 3_400_000L, "Rent", ExpenseClassification.COMMITTED),
        expense("b", "2026-10-02", 900_000L, "Shopping"),
        expense("c", "2026-10-03", 800_000L, "Food Delivery"),
        expense("d", "2026-10-04", 700_000L, "Food Delivery"),
        expense("e", "2026-10-05", 100_000L, "Transport"),
        expense("f", "2026-10-06", 50_000L, "Transport"),
        expense("g", "2026-10-07", 25_000L, "Coffee")
    )

    @Test
    fun topThreeCategoriesAndLargestCategory() {
        val insights = SpendingInsightsCalculator.build(rows)
        assertEquals(listOf("Rent", "Food Delivery", "Shopping"), insights.topCategories.map { it.categoryName })
        assertEquals("Rent", insights.largestCategory?.categoryName)
    }

    @Test
    fun fiveLargestExpensesAreOrderedByAmount() {
        val insights = SpendingInsightsCalculator.build(rows)
        assertEquals(listOf("a", "b", "c", "d", "e"), insights.largestExpenses.map { it.id })
    }

    @Test
    fun discretionaryShareIsRoundedToOneDecimal() {
        // total 5,975,000; discretionary 2,575,000 = 43.1%
        assertEquals(43.1, SpendingInsightsCalculator.build(rows).discretionaryPercent, 0.0)
    }

    @Test
    fun emptyInputGivesEmptyInsights() {
        val insights = SpendingInsightsCalculator.build(emptyList())
        assertNull(insights.largestCategory)
        assertEquals(0, insights.topCategories.size)
        assertEquals(0, insights.largestExpenses.size)
        assertEquals(0.0, insights.discretionaryPercent, 0.0)
    }
}
