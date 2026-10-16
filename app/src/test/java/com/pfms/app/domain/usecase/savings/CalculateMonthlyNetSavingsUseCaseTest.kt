package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class CalculateMonthlyNetSavingsUseCaseTest {

    private lateinit var useCase: CalculateMonthlyNetSavingsUseCase

    @Before
    fun setup() {
        useCase = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
    }

    @Test
    fun `current month net savings is calculated correctly as income minus expenses`() {
        val targetMonth = YearMonth.of(2026, 10)
        val incomes = listOf(
            Income(id = "inc_1", amountMinor = 150_000L, source = "Salary", receivedDate = "2026-10-01"),
            Income(id = "inc_2", amountMinor = 50_000L, source = "Freelance", paymentStatus = "Received", receivedDate = "2026-10-15"),
            Income(id = "inc_unpaid", amountMinor = 80_000L, source = "Freelance", paymentStatus = "Expected", receivedDate = "2026-10-20") // excluded
        )
        val expenses = listOf(
            Expense(id = "exp_1", amountMinor = 40_000L, date = "2026-10-03"),
            Expense(id = "exp_2", amountMinor = 35_000L, date = "2026-10-12"),
            Expense(id = "exp_past", amountMinor = 50_000L, date = "2026-09-20") // excluded
        )

        val netSavings = useCase(incomes, expenses, targetMonth)

        // Realized income = 150,000 + 50,000 = 200,000
        // Expenses = 40,000 + 35,000 = 75,000
        // Net savings = 200,000 - 75,000 = 125,000
        assertEquals(125_000L, netSavings)
    }

    @Test
    fun `net savings handles empty expenses safely by treating expenses as zero`() {
        val targetMonth = YearMonth.of(2026, 10)
        val incomes = listOf(
            Income(id = "inc_1", amountMinor = 100_000L, source = "Salary", receivedDate = "2026-10-01")
        )

        val netSavings = useCase(incomes, emptyList(), targetMonth)

        assertEquals(100_000L, netSavings)
    }

    @Test
    fun `net savings can be negative if expenses exceed realized income`() {
        val targetMonth = YearMonth.of(2026, 10)
        val incomes = listOf(
            Income(id = "inc_1", amountMinor = 50_000L, source = "Salary", receivedDate = "2026-10-01")
        )
        val expenses = listOf(
            Expense(id = "exp_1", amountMinor = 80_000L, date = "2026-10-05")
        )

        val netSavings = useCase(incomes, expenses, targetMonth)

        // 50,000 - 80,000 = -30,000
        assertEquals(-30_000L, netSavings)
    }
}
