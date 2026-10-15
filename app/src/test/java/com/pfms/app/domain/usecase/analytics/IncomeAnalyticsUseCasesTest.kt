package com.pfms.app.domain.usecase.analytics

import com.pfms.app.domain.model.Income
import com.pfms.app.domain.model.analytics.EstimationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class IncomeAnalyticsUseCasesTest {

    private lateinit var getMonthlySummary: GetMonthlyIncomeSummaryUseCase
    private lateinit var getRegularVsVariable: GetRegularVsVariableIncomeUseCase
    private lateinit var getTrends: GetIncomeTrendsUseCase
    private lateinit var getTrailingAverage: GetTrailingThreeMonthAverageUseCase
    private lateinit var getEstimationComparison: GetIncomeEstimationComparisonUseCase
    private lateinit var getAnalytics: GetIncomeAnalyticsUseCase

    private val oct2026 = YearMonth.of(2026, 10)

    @Before
    fun setup() {
        getMonthlySummary = GetMonthlyIncomeSummaryUseCase()
        getRegularVsVariable = GetRegularVsVariableIncomeUseCase(getMonthlySummary)
        getTrends = GetIncomeTrendsUseCase(getMonthlySummary)
        getTrailingAverage = GetTrailingThreeMonthAverageUseCase(getMonthlySummary)
        getEstimationComparison = GetIncomeEstimationComparisonUseCase(getTrailingAverage)
        getAnalytics = GetIncomeAnalyticsUseCase(
            getMonthlyIncomeSummaryUseCase = getMonthlySummary,
            getRegularVsVariableIncomeUseCase = getRegularVsVariable,
            getIncomeTrendsUseCase = getTrends,
            getIncomeEstimationComparisonUseCase = getEstimationComparison
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 1–9: MONTHLY SUMMARY TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `1 Salary is included in monthly summary`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 25000000L, receivedDate = "2026-10-25")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(25000000L, summary.salaryMinor)
        assertEquals(25000000L, summary.totalRealizedMinor)
    }

    @Test
    fun `2 Received freelance is included in monthly summary`() {
        val incomes = listOf(
            Income(id = "1", source = "Freelance", amountMinor = 5000000L, paymentStatus = "Received", receivedDate = "2026-10-15")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(5000000L, summary.freelanceMinor)
        assertEquals(5000000L, summary.totalRealizedMinor)
    }

    @Test
    fun `3 Expected freelance is excluded from monthly summary`() {
        val incomes = listOf(
            Income(id = "1", source = "Freelance", amountMinor = 8000000L, paymentStatus = "Expected", receivedDate = "2026-10-15")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(0L, summary.freelanceMinor)
        assertEquals(0L, summary.totalRealizedMinor)
    }

    @Test
    fun `4 Google AdSense is included in monthly summary`() {
        val incomes = listOf(
            Income(id = "1", source = "Google AdSense", amountMinor = 3500000L, receivedDate = "2026-10-21")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(3500000L, summary.adsenseMinor)
        assertEquals(3500000L, summary.totalRealizedMinor)
    }

    @Test
    fun `5 Crypto gain adds to cryptoNet and total`() {
        val incomes = listOf(
            Income(id = "1", source = "Crypto", cryptoType = "Gain", amountMinor = 4000000L, receivedDate = "2026-10-10")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(4000000L, summary.cryptoNetMinor)
        assertEquals(4000000L, summary.totalRealizedMinor)
    }

    @Test
    fun `6 Crypto loss subtracts from cryptoNet and total`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 20000000L, receivedDate = "2026-10-01"),
            Income(id = "2", source = "Crypto", cryptoType = "Loss", amountMinor = 3000000L, receivedDate = "2026-10-12")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(-3000000L, summary.cryptoNetMinor)
        assertEquals(17000000L, summary.totalRealizedMinor)
    }

    @Test
    fun `7 Other source is included in monthly summary`() {
        val incomes = listOf(
            Income(id = "1", source = "Other", amountMinor = 1200000L, receivedDate = "2026-10-05")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(1200000L, summary.otherMinor)
        assertEquals(1200000L, summary.totalRealizedMinor)
    }

    @Test
    fun `8 USD income uses stored amountLkrMinor converted value`() {
        val incomes = listOf(
            Income(
                id = "1",
                source = "Freelance",
                currency = "USD",
                amountMinor = 100000L, // $1,000.00
                amountLkrMinor = 30000000L, // LKR 300,000.00 at stored rate 300.00
                paymentStatus = "Received",
                receivedDate = "2026-10-14"
            )
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(30000000L, summary.freelanceMinor)
        assertEquals(30000000L, summary.totalRealizedMinor)
    }

    @Test
    fun `9 Income outside selected month is excluded`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 25000000L, receivedDate = "2026-09-25"),
            Income(id = "2", source = "Salary", amountMinor = 26000000L, receivedDate = "2026-10-25"),
            Income(id = "3", source = "Salary", amountMinor = 27000000L, receivedDate = "2026-11-25")
        )

        val summary = getMonthlySummary(incomes, oct2026)

        assertEquals(26000000L, summary.salaryMinor)
        assertEquals(26000000L, summary.totalRealizedMinor)
    }

    // ─────────────────────────────────────────────────────────────
    // 10–13: REGULAR VS VARIABLE TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `10 Salary is classified as regular income`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 20000000L, receivedDate = "2026-10-25")
        )

        val rv = getRegularVsVariable(incomes, oct2026)

        assertEquals(20000000L, rv.regularMinor)
        assertEquals(0L, rv.variableMinor)
        assertEquals(20000000L, rv.totalMinor)
        assertEquals(100.0, rv.regularPercentage, 0.01)
        assertEquals(0.0, rv.variablePercentage, 0.01)
    }

    @Test
    fun `11 All other sources are classified as variable income`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 10000000L, receivedDate = "2026-10-01"),
            Income(id = "2", source = "Freelance", amountMinor = 5000000L, paymentStatus = "Received", receivedDate = "2026-10-02"),
            Income(id = "3", source = "Google AdSense", amountMinor = 2000000L, receivedDate = "2026-10-03"),
            Income(id = "4", source = "Crypto", cryptoType = "Gain", amountMinor = 2000000L, receivedDate = "2026-10-04"),
            Income(id = "5", source = "Other", amountMinor = 1000000L, receivedDate = "2026-10-05")
        )

        val rv = getRegularVsVariable(incomes, oct2026)

        assertEquals(10000000L, rv.regularMinor)
        assertEquals(10000000L, rv.variableMinor) // 5m + 2m + 2m + 1m
        assertEquals(20000000L, rv.totalMinor)
        assertEquals(50.0, rv.regularPercentage, 0.01)
        assertEquals(50.0, rv.variablePercentage, 0.01)
    }

    @Test
    fun `12 Zero total handles percentage safely without division by zero`() {
        val rv = getRegularVsVariable(emptyList(), oct2026)

        assertEquals(0L, rv.regularMinor)
        assertEquals(0L, rv.variableMinor)
        assertEquals(0L, rv.totalMinor)
        assertEquals(0.0, rv.regularPercentage, 0.0)
        assertEquals(0.0, rv.variablePercentage, 0.0)
    }

    @Test
    fun `13 Crypto loss is handled correctly in regular vs variable calculation`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 15000000L, receivedDate = "2026-10-01"),
            Income(id = "2", source = "Crypto", cryptoType = "Loss", amountMinor = 5000000L, receivedDate = "2026-10-05")
        )

        val rv = getRegularVsVariable(incomes, oct2026)

        assertEquals(15000000L, rv.regularMinor)
        assertEquals(-5000000L, rv.variableMinor)
        assertEquals(10000000L, rv.totalMinor)
        assertEquals(150.0, rv.regularPercentage, 0.01)
        assertEquals(-50.0, rv.variablePercentage, 0.01)
    }

    // ─────────────────────────────────────────────────────────────
    // 14–16: TRENDS TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `14 Trends produces continuous points ordered oldest to newest`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 20000000L, receivedDate = "2026-06-15"),
            Income(id = "2", source = "Salary", amountMinor = 25000000L, receivedDate = "2026-10-15")
        )

        val points = getTrends(incomes, monthCount = 6, referenceMonth = oct2026)

        assertEquals(6, points.size)
        assertEquals("2026-05", points[0].yearMonth)
        assertEquals("2026-06", points[1].yearMonth)
        assertEquals("2026-07", points[2].yearMonth)
        assertEquals("2026-08", points[3].yearMonth)
        assertEquals("2026-09", points[4].yearMonth)
        assertEquals("2026-10", points[5].yearMonth)

        assertEquals(20000000L, points[1].amountMinor)
        assertEquals(25000000L, points[5].amountMinor)
    }

    @Test
    fun `15 Zero income months have 0 amount for continuous charting`() {
        val points = getTrends(emptyList(), monthCount = 3, referenceMonth = oct2026)

        assertEquals(3, points.size)
        assertEquals(0L, points[0].amountMinor)
        assertEquals(0L, points[1].amountMinor)
        assertEquals(0L, points[2].amountMinor)
    }

    @Test
    fun `16 Configurable monthCount filters correct window`() {
        val points = getTrends(emptyList(), monthCount = 12, referenceMonth = oct2026)

        assertEquals(12, points.size)
        assertEquals("2025-11", points.first().yearMonth)
        assertEquals("2026-10", points.last().yearMonth)
    }

    // ─────────────────────────────────────────────────────────────
    // 17–22: ESTIMATION & TRAILING 3-MONTH AVERAGE TESTS
    // ─────────────────────────────────────────────────────────────

    @Test
    fun `17 Trailing 3-calendar-month average calculates correct calendar average`() {
        // Preceding completed months: Jul (2026-07), Aug (2026-08), Sep (2026-09)
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-07-25"),
            Income(id = "2", source = "Salary", amountMinor = 40000000L, receivedDate = "2026-08-25"),
            Income(id = "3", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-09-25"),
            // October income should NOT be in the completed 3-month trailing window by default
            Income(id = "4", source = "Salary", amountMinor = 100000000L, receivedDate = "2026-10-25")
        )

        val result = getTrailingAverage(incomes, referenceMonth = oct2026, includeCurrentMonth = false)

        // (30m + 40m + 50m) / 3 = 40m
        assertEquals(40000000L, result.averageMinor)
        assertEquals(listOf("2026-07", "2026-08", "2026-09"), result.monthsEvaluated)
    }

    @Test
    fun `18 Estimation comparison categorizes ABOVE_ESTIMATE when actual exceeds estimate`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 60000000L, receivedDate = "2026-07-15"),
            Income(id = "2", source = "Salary", amountMinor = 60000000L, receivedDate = "2026-08-15"),
            Income(id = "3", source = "Salary", amountMinor = 60000000L, receivedDate = "2026-09-15")
        )
        // Trailing avg = 60,000,000. Estimate = 50,000,000. Difference = +10,000,000 (+20%)
        val comp = getEstimationComparison(
            estimatedMonthlyIncomeMinor = 50000000L,
            incomes = incomes,
            referenceMonth = oct2026
        )

        assertEquals(EstimationStatus.ABOVE_ESTIMATE, comp.status)
        assertEquals(10000000L, comp.differenceMinor)
        assertEquals(20.0, comp.variancePercentage, 0.01)
    }

    @Test
    fun `19 Estimation comparison categorizes BELOW_ESTIMATE when actual is less than estimate`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-07-15"),
            Income(id = "2", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-08-15"),
            Income(id = "3", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-09-15")
        )
        // Trailing avg = 30,000,000. Estimate = 50,000,000. Difference = -20,000,000 (-40%)
        val comp = getEstimationComparison(
            estimatedMonthlyIncomeMinor = 50000000L,
            incomes = incomes,
            referenceMonth = oct2026
        )

        assertEquals(EstimationStatus.BELOW_ESTIMATE, comp.status)
        assertEquals(-20000000L, comp.differenceMinor)
        assertEquals(-40.0, comp.variancePercentage, 0.01)
    }

    @Test
    fun `20 Estimation comparison categorizes ON_TARGET when actual equals estimate`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-07-15"),
            Income(id = "2", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-08-15"),
            Income(id = "3", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-09-15")
        )
        val comp = getEstimationComparison(
            estimatedMonthlyIncomeMinor = 50000000L,
            incomes = incomes,
            referenceMonth = oct2026
        )

        assertEquals(EstimationStatus.ON_TARGET, comp.status)
        assertEquals(0L, comp.differenceMinor)
        assertEquals(0.0, comp.variancePercentage, 0.01)
    }

    @Test
    fun `21 Zero estimate is handled safely without division by zero`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-07-15"),
            Income(id = "2", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-08-15"),
            Income(id = "3", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-09-15")
        )

        val comp = getEstimationComparison(
            estimatedMonthlyIncomeMinor = 0L,
            incomes = incomes,
            referenceMonth = oct2026
        )

        assertEquals(EstimationStatus.ABOVE_ESTIMATE, comp.status)
        assertEquals(30000000L, comp.differenceMinor)
        assertEquals(0.0, comp.variancePercentage, 0.0) // Division by zero protected
    }

    @Test
    fun `22 Insufficient history divides total by 3 calendar months safely`() {
        // User has only 1 month of history
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-09-15")
        )

        val result = getTrailingAverage(incomes, referenceMonth = oct2026)

        // 30,000,000 / 3 = 10,000,000
        assertEquals(10000000L, result.averageMinor)
    }

    @Test
    fun `23 Aggregator GetIncomeAnalyticsUseCase produces complete summary`() {
        val incomes = listOf(
            Income(id = "1", source = "Salary", amountMinor = 30000000L, receivedDate = "2026-07-15"),
            Income(id = "2", source = "Salary", amountMinor = 40000000L, receivedDate = "2026-08-15"),
            Income(id = "3", source = "Salary", amountMinor = 50000000L, receivedDate = "2026-09-15"),
            Income(id = "4", source = "Salary", amountMinor = 60000000L, receivedDate = "2026-10-15")
        )

        val summary = getAnalytics(
            incomes = incomes,
            targetMonth = oct2026,
            estimatedMonthlyIncomeMinor = 45000000L,
            trendMonthCount = 6
        )

        assertNotNull(summary.selectedMonthSummary)
        assertEquals(60000000L, summary.selectedMonthSummary?.totalRealizedMinor)
        assertEquals(60000000L, summary.regularVsVariable.regularMinor)
        assertEquals(6, summary.trendPoints.size)
        assertEquals("2026-10", summary.highestIncomeMonth)
        assertEquals(60000000L, summary.highestIncomeMinor)
        assertNotNull(summary.estimationComparison)
        assertEquals(40000000L, summary.estimationComparison?.trailingThreeMonthAverageMinor)
        assertEquals(EstimationStatus.BELOW_ESTIMATE, summary.estimationComparison?.status)
    }
}
