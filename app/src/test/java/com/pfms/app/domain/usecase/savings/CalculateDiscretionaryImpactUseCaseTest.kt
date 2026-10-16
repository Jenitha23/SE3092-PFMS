package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class CalculateDiscretionaryImpactUseCaseTest {

    private lateinit var useCase: CalculateDiscretionaryImpactUseCase

    @Before
    fun setup() {
        useCase = CalculateDiscretionaryImpactUseCase()
    }

    @Test
    fun `discretionary expenses only are included in discretionary total and impact percentage`() {
        val targetMonth = YearMonth.of(2026, 10)
        val expenses = listOf(
            Expense(id = "e1", amountMinor = 25_000L, classification = ExpenseClassification.DISCRETIONARY, date = "2026-10-02"),
            Expense(id = "e2", amountMinor = 15_000L, classification = ExpenseClassification.DISCRETIONARY, date = "2026-10-15")
        )
        val requiredSaving = 100_000L

        val impact = useCase(expenses, targetMonth, requiredSaving)

        // discretionary = 25,000 + 15,000 = 40,000
        // percentage = 40,000 / 100,000 * 100 = 40.0%
        assertEquals(40_000L, impact.discretionaryExpensesMinor)
        assertEquals(0L, impact.committedExpensesMinor)
        assertEquals(40_000L, impact.totalExpensesMinor)
        assertEquals(40.0, impact.percentageOfRequiredSaving, 0.001)
    }

    @Test
    fun `committed expenses are excluded from discretionary total`() {
        val targetMonth = YearMonth.of(2026, 10)
        val expenses = listOf(
            Expense(id = "e1", amountMinor = 30_000L, classification = ExpenseClassification.DISCRETIONARY, date = "2026-10-02"),
            Expense(id = "e2", amountMinor = 80_000L, classification = ExpenseClassification.COMMITTED, date = "2026-10-05") // rent/bills: excluded
        )
        val requiredSaving = 60_000L

        val impact = useCase(expenses, targetMonth, requiredSaving)

        // Only e1 counts for discretionary: 30,000
        // percentage = 30,000 / 60,000 * 100 = 50.0%
        assertEquals(30_000L, impact.discretionaryExpensesMinor)
        assertEquals(80_000L, impact.committedExpensesMinor)
        assertEquals(110_000L, impact.totalExpensesMinor)
        assertEquals(50.0, impact.percentageOfRequiredSaving, 0.001)
    }

    @Test
    fun `percentage is calculated correctly when discretionary expenses exceed required saving`() {
        val targetMonth = YearMonth.of(2026, 10)
        val expenses = listOf(
            Expense(id = "e1", amountMinor = 120_000L, classification = ExpenseClassification.DISCRETIONARY, date = "2026-10-10")
        )
        val requiredSaving = 100_000L

        val impact = useCase(expenses, targetMonth, requiredSaving)

        // 120,000 / 100,000 * 100 = 120.0%
        assertEquals(120.0, impact.percentageOfRequiredSaving, 0.001)
    }

    @Test
    fun `zero or negative required monthly saving is handled safely without division by zero`() {
        val targetMonth = YearMonth.of(2026, 10)
        val expenses = listOf(
            Expense(id = "e1", amountMinor = 50_000L, classification = ExpenseClassification.DISCRETIONARY, date = "2026-10-10")
        )

        val impactZero = useCase(expenses, targetMonth, requiredMonthlySavingMinor = 0L)
        val impactNegative = useCase(expenses, targetMonth, requiredMonthlySavingMinor = -10_000L)

        assertEquals(0.0, impactZero.percentageOfRequiredSaving, 0.001)
        assertEquals(0.0, impactNegative.percentageOfRequiredSaving, 0.001)
    }

    @Test
    fun `empty expense list results in zero percent and zero expenses`() {
        val targetMonth = YearMonth.of(2026, 10)

        val impact = useCase(emptyList(), targetMonth, requiredMonthlySavingMinor = 50_000L)

        assertEquals(0L, impact.discretionaryExpensesMinor)
        assertEquals(0L, impact.committedExpensesMinor)
        assertEquals(0.0, impact.percentageOfRequiredSaving, 0.001)
    }
}
