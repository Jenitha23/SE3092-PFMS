
package com.pfms.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Unit tests for [CurrencyConverter].
 *
 * These verify that the BigDecimal-based conversion avoids
 * floating-point rounding errors and correctly converts USD
 * minor units (cents) to LKR minor units (cents).
 */
class CurrencyConverterTest {

    // ─────────────────────────────────────────────────────
    // Basic conversion correctness
    // ─────────────────────────────────────────────────────

    @Test
    fun `convert 100 USD to LKR at rate 305_42`() {
        // 100.00 USD * 305.42 = 30,542.00 LKR = 3,054,200 minor
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 10000L,
            exchangeRate = BigDecimal("305.42")
        )
        assertEquals(3054200L, result)
    }

    @Test
    fun `convert 1 cent USD to LKR at rate 305_42`() {
        // 0.01 USD * 305.42 = 3.0542 LKR → rounded = 3.05 LKR = 305 minor
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 1L,
            exchangeRate = BigDecimal("305.42")
        )
        assertEquals(305L, result)
    }

    @Test
    fun `convert 100_50 USD to LKR at rate 305_42`() {
        // 100.50 * 305.42 = 30,694.71 LKR = 3,069,471 minor
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 10050L,
            exchangeRate = BigDecimal("305.42")
        )
        assertEquals(3069471L, result)
    }

    // ─────────────────────────────────────────────────────
    // Rounding edge cases (HALF_UP)
    // ─────────────────────────────────────────────────────

    @Test
    fun `rounding half up at exact midpoint`() {
        // 1.00 USD * 100.005 = 100.005 → HALF_UP → 100.01 = 10001 minor
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 100L,
            exchangeRate = BigDecimal("100.005")
        )
        assertEquals(10001L, result)
    }

    @Test
    fun `rounding down below midpoint`() {
        // 1.00 USD * 100.004 = 100.004 → rounds to 100.00 = 10000 minor
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 100L,
            exchangeRate = BigDecimal("100.004")
        )
        assertEquals(10000L, result)
    }

    // ─────────────────────────────────────────────────────
    // Floating-point trap verification
    // ─────────────────────────────────────────────────────

    @Test
    fun `avoids floating point imprecision for 2_98 times 100`() {
        // A classic Double trap: 2.98 * 100 can equal 297.99999…
        // BigDecimal must produce exactly 298.00 → 29800 minor.
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 298L,
            exchangeRate = BigDecimal("100")
        )
        assertEquals(29800L, result)
    }

    @Test
    fun `large amount conversion stays precise`() {
        // 50,000.00 USD * 305.42 = 15,271,000.00 LKR
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 5000000L,
            exchangeRate = BigDecimal("305.42")
        )
        assertEquals(1527100000L, result)
    }

    // ─────────────────────────────────────────────────────
    // Zero and boundary
    // ─────────────────────────────────────────────────────

    @Test
    fun `zero USD converts to zero LKR`() {
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 0L,
            exchangeRate = BigDecimal("305.42")
        )
        assertEquals(0L, result)
    }

    @Test
    fun `rate of 1 returns same value`() {
        // 50.00 USD * 1.00 = 50.00 LKR = 5000 minor
        val result = CurrencyConverter.convertUsdToLkrMinor(
            amountUsdMinor = 5000L,
            exchangeRate = BigDecimal("1.00")
        )
        assertEquals(5000L, result)
    }
}
