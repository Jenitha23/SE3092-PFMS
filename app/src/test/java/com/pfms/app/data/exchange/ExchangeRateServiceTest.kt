
package com.pfms.app.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [ExchangeRateService].
 *
 * These are integration-level tests that hit the real Frankfurter API.
 * They verify that:
 * 1. The service can successfully fetch the latest USD → LKR rate.
 * 2. The service can fetch a historical rate.
 * 3. The returned rate is a plausible positive number.
 *
 * NOTE: These tests require network access. If the CI environment
 * blocks outbound HTTP, annotate with @Ignore or move to androidTest.
 */
class ExchangeRateServiceTest {

    private val service = ExchangeRateService()

    @Test
    fun `fetchLatestRate returns a positive LKR rate`() {
        val result = service.fetchLatestRate()

        assertTrue(
            "Expected Success but got: $result",
            result is ExchangeRateResult.Success
        )

        val success = result as ExchangeRateResult.Success

        assertTrue(
            "Rate should be positive, got: ${success.rate}",
            success.rate > BigDecimal.ZERO
        )

        assertNotNull(
            "Rate date should not be null",
            success.rateDate
        )

        // Sanity: LKR/USD rate should be > 100 (it has been well above 200 since 2022).
        assertTrue(
            "LKR rate seems too low: ${success.rate}",
            success.rate > BigDecimal("100")
        )
    }

    @Test
    fun `fetchHistoricalRate returns a rate for a past date when available`() {
        val result = service.fetchHistoricalRate("2026-10-10")

        assertTrue(
            "Expected Success but got: $result",
            result is ExchangeRateResult.Success
        )

        val success = result as ExchangeRateResult.Success

        assertTrue(
            "Historical rate should be positive",
            success.rate > BigDecimal.ZERO
        )
    }

    @Test
    fun `fetchHistoricalRate returns Failure without silent fallback when unavailable`() {
        val result = service.fetchHistoricalRate("2010-01-01")

        assertTrue(
            "Expected Failure for unavailable historical date but got: $result",
            result is ExchangeRateResult.Failure
        )
    }
}
