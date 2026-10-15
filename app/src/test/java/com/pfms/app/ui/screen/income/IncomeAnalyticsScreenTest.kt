package com.pfms.app.ui.screen.income

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class IncomeAnalyticsScreenTest {

    @Test
    fun `formatAnalyticsLkrAmount formats standard positive minor units correctly`() {
        // 250,000.00 LKR = 25,000,000 minor units
        val formatted = formatAnalyticsLkrAmount(25000000L)
        assertEquals("LKR 250,000.00", formatted)
    }

    @Test
    fun `formatAnalyticsLkrAmount formats zero correctly`() {
        val formatted = formatAnalyticsLkrAmount(0L)
        assertEquals("LKR 0.00", formatted)
    }

    @Test
    fun `formatAnalyticsLkrAmount formats negative values with negative sign`() {
        val formatted = formatAnalyticsLkrAmount(-500000L)
        assertEquals("- LKR 5,000.00", formatted)
    }

    @Test
    fun `formatMonthLong formats YearMonth to month name and year`() {
        val ym = YearMonth.of(2026, 10)
        assertEquals("October 2026", formatMonthLong(ym))
    }

    @Test
    fun `formatMonthShort formats YYYY-MM to short month name`() {
        assertEquals("Oct", formatMonthShort("2026-10"))
        assertEquals("May", formatMonthShort("2026-05"))
    }

    @Test
    fun `parseInputToMinor parses decimal strings to integer minor units`() {
        assertEquals(15000000L, parseInputToMinor("150000"))
        assertEquals(15000050L, parseInputToMinor("150000.50"))
        assertEquals(0L, parseInputToMinor("0"))
    }

    @Test
    fun `parseInputToMinor returns null for invalid and negative inputs`() {
        assertNull(parseInputToMinor(""))
        assertNull(parseInputToMinor("   "))
        assertNull(parseInputToMinor("-50"))
        assertNull(parseInputToMinor("abc"))
    }
}
