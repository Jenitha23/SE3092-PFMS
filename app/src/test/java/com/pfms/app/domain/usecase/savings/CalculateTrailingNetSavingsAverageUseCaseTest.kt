package com.pfms.app.domain.usecase.savings

import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class CalculateTrailingNetSavingsAverageUseCaseTest {

    private lateinit var useCase: CalculateTrailingNetSavingsAverageUseCase

    @Before
    fun setup() {
        val monthlyNetSavingsUseCase = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
        useCase = CalculateTrailingNetSavingsAverageUseCase(monthlyNetSavingsUseCase)
    }

    @Test
    fun `trailing 3 calendar month net savings average is calculated across M-3 M-2 M-1`() {
        val refMonth = YearMonth.of(2026, 10)
        // Evaluates:
        // M-3: 2026-07: income 100,000, expenses 40,000 -> net = 60,000
        // M-2: 2026-08: income 120,000, expenses 30,000 -> net = 90,000
        // M-1: 2026-09: income 150,000, expenses 60,000 -> net = 90,000
        // M:   2026-10: (ignored as ongoing month)
        val incomes = listOf(
            Income(id = "i1", amountMinor = 100_000L, source = "Salary", receivedDate = "2026-07-10"),
            Income(id = "i2", amountMinor = 120_000L, source = "Salary", receivedDate = "2026-08-10"),
            Income(id = "i3", amountMinor = 150_000L, source = "Salary", receivedDate = "2026-09-10"),
            Income(id = "i4", amountMinor = 200_000L, source = "Salary", receivedDate = "2026-10-10")
        )
        val expenses = listOf(
            Expense(id = "e1", amountMinor = 40_000L, date = "2026-07-15"),
            Expense(id = "e2", amountMinor = 30_000L, date = "2026-08-15"),
            Expense(id = "e3", amountMinor = 60_000L, date = "2026-09-15"),
            Expense(id = "e4", amountMinor = 50_000L, date = "2026-10-15")
        )

        val result = useCase(incomes, expenses, refMonth)

        // total = 60,000 + 90,000 + 90,000 = 240,000
        // average = 240,000 / 3.0 = 80,000
        assertEquals(240_000L, result.totalMinor)
        assertEquals(80_000L, result.averageMinor)
        assertEquals(listOf("2026-07", "2026-08", "2026-09"), result.monthsEvaluated)
    }

    @Test
    fun `months with no transactions count as zero in trailing average`() {
        val refMonth = YearMonth.of(2026, 10)
        // M-3: 2026-07: no transactions -> 0
        // M-2: 2026-08: no transactions -> 0
        // M-1: 2026-09: income 90,000, expenses 0 -> net = 90,000
        val incomes = listOf(
            Income(id = "i1", amountMinor = 90_000L, source = "Salary", receivedDate = "2026-09-10")
        )

        val result = useCase(incomes, emptyList(), refMonth)

        // total = 0 + 0 + 90,000 = 90,000
        // average = 90,000 / 3.0 = 30,000
        assertEquals(90_000L, result.totalMinor)
        assertEquals(30_000L, result.averageMinor)
    }
}
