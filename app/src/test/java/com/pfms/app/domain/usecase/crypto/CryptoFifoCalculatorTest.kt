package com.pfms.app.domain.usecase.crypto

import com.pfms.app.domain.model.CryptoSide
import com.pfms.app.domain.model.CryptoTrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoFifoCalculatorTest {
    private fun trade(id: String, side: CryptoSide, qty: String, gross: Long, fee: Long, date: String) =
        CryptoTrade(id,"uid","BTC",side,qty,gross,fee,date,1L)

    @Test fun `full sale deducts buy and sell fees`() {
        val buy = trade("b",CryptoSide.BUY,"1",5_000_000,20_000,"2026-01-01")
        val sell = trade("s",CryptoSide.SELL,"1",5_800_000,30_000,"2026-02-01")
        assertEquals(750_000L, CryptoFifoCalculator.calculateSale(listOf(buy), sell).signedRealizedMinor)
    }

    @Test fun `partial sales use oldest cost basis`() {
        val first = trade("b1",CryptoSide.BUY,"1",100_000,0,"2026-01-01")
        val second = trade("b2",CryptoSide.BUY,"1",200_000,0,"2026-01-02")
        val firstSale = trade("s1",CryptoSide.SELL,"0.5",100_000,0,"2026-01-03")
        val lastSale = trade("s2",CryptoSide.SELL,"1",200_000,0,"2026-01-04")
        val a = CryptoFifoCalculator.calculateSale(listOf(first,second), firstSale)
        assertEquals(50_000L,a.allocatedCostMinor)
        val b = CryptoFifoCalculator.calculateSale(listOf(first,second,firstSale),lastSale)
        assertEquals(150_000L,b.allocatedCostMinor)
        assertEquals(50_000L,b.signedRealizedMinor)
    }

    @Test fun `overselling fails`() {
        val buy = trade("b",CryptoSide.BUY,"0.1",10_000,0,"2026-01-01")
        val sell = trade("s",CryptoSide.SELL,"0.2",20_000,0,"2026-02-01")
        assertThrows(IllegalArgumentException::class.java) {
            CryptoFifoCalculator.calculateSale(listOf(buy),sell)
        }
    }
}
