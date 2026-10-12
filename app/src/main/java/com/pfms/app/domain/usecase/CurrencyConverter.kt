
package com.pfms.app.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts a USD amount (in minor units / cents) to LKR minor units
 * using the given exchange rate.
 *
 * All arithmetic uses [BigDecimal] to prevent floating-point rounding
 * errors in financial calculations. The result is rounded HALF_UP to
 * the nearest cent (minor unit).
 *
 * Example:
 *   amountUsdMinor = 10050  (i.e. USD 100.50)
 *   exchangeRate   = 305.42
 *   result         = 100.50 * 305.42 = 30,694.71 → 3,069,471 minor
 *
 * @param amountUsdMinor  The USD amount expressed in cents (Long).
 * @param exchangeRate    The rate: 1 USD = [exchangeRate] LKR.
 * @return The equivalent LKR amount in minor units (cents).
 */
object CurrencyConverter {

    fun convertUsdToLkrMinor(
        amountUsdMinor: Long,
        exchangeRate: BigDecimal
    ): Long {
        // Convert cents → dollars.
        val usdMajor = BigDecimal.valueOf(amountUsdMinor, 2)

        // Multiply by rate → LKR major.
        val lkrMajor = usdMajor.multiply(exchangeRate)

        // Convert LKR major → minor (cents), rounded HALF_UP.
        return lkrMajor
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }
}
