package com.pfms.app.domain.usecase

import java.math.BigDecimal
import com.pfms.app.domain.model.Income

/** The same exact monetary limits apply to the entry preview and persisted income. */
object IncomeEntryAmount {
    fun parseMinor(value: String): Long? = runCatching {
        val amount = value.trim().toBigDecimal()
        require(amount > BigDecimal.ZERO && amount.scale() <= 2)
        amount.movePointRight(2).longValueExact()
    }.getOrNull()

    fun increment(value: String, majorUnits: Long): String? = runCatching {
        val current = if (value.isBlank()) BigDecimal.ZERO else value.trim().toBigDecimal()
        require(current >= BigDecimal.ZERO && current.scale() <= 2 && majorUnits > 0)
        val next = current.add(BigDecimal.valueOf(majorUnits)).setScale(2)
        require(parseMinor(next.toPlainString()) != null)
        next.toPlainString()
    }.getOrNull()

    fun lkrPreview(value: String, rate: BigDecimal?, original: Income? = null, receivedDate: String? = null): Long? {
        val amount = parseMinor(value) ?: return null
        val positiveRate = rate?.takeIf { it > BigDecimal.ZERO } ?: return null
        if (original?.currency == "USD" && original.receivedDate == receivedDate &&
            original.amountMinor == amount && original.exchangeRate?.toBigDecimalOrNull()?.compareTo(positiveRate) == 0 &&
            original.amountLkrMinor != null) return original.amountLkrMinor
        return runCatching { CurrencyConverter.convertUsdToLkrMinor(amount, positiveRate) }.getOrNull()
    }
}
