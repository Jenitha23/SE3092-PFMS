package com.pfms.app.domain.usecase

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import com.pfms.app.domain.model.Income

class IncomeEntryAmountTest {
    @Test fun exactMinorUnitsAndStorageLimits() {
        assertEquals(12345L, IncomeEntryAmount.parseMinor("123.45"))
        assertEquals(Long.MAX_VALUE, IncomeEntryAmount.parseMinor("92233720368547758.07"))
        listOf("", "0", "-10", "1.234", "92233720368547758.08").forEach { assertNull(IncomeEntryAmount.parseMinor(it)) }
    }

    @Test fun incrementsDoNotRoundInvalidInputOrOverflow() {
        assertEquals("5000.00", IncomeEntryAmount.increment("", 5000))
        assertEquals("5012.34", IncomeEntryAmount.increment("12.34", 5000))
        assertNull(IncomeEntryAmount.increment("12.345", 5000))
        assertNull(IncomeEntryAmount.increment("92233720368547758.07", 5000))
    }

    @Test fun previewUsesTheSameRoundingAsSavedConversion() {
        assertEquals(305725L, IncomeEntryAmount.lkrPreview("10.01", BigDecimal("305.42")))
        assertNull(IncomeEntryAmount.lkrPreview("10.005", BigDecimal("305.42")))
        assertNull(IncomeEntryAmount.lkrPreview("100", BigDecimal.ZERO))
        assertNull(IncomeEntryAmount.lkrPreview("92233720368547758.07", BigDecimal("305.42")))
    }

    @Test fun unchangedHistoricalEntryDisplaysItsExactSavedConversion() {
        val original = Income("usd", "a", 10000, "USD", receivedDate = "2026-09-15", exchangeRate = "300.25", amountLkrMinor = 3002501)
        assertEquals(3002501L, IncomeEntryAmount.lkrPreview("100", BigDecimal("300.25"), original, original.receivedDate))
        assertEquals(6005000L, IncomeEntryAmount.lkrPreview("200", BigDecimal("300.25"), original, original.receivedDate))
        assertEquals(3002500L, IncomeEntryAmount.lkrPreview("100", BigDecimal("300.25"), original, "2026-09-16"))
    }
}
