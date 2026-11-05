package com.pfms.app.domain.usecase.crypto

import com.pfms.app.domain.model.CryptoSide
import com.pfms.app.domain.model.CryptoTrade
import java.math.BigDecimal
import java.math.RoundingMode

/** Pure calculation. All currency inputs are already LKR minor units. */
object CryptoFifoCalculator {
    data class SaleResult(
        val allocatedCostMinor: Long,
        val netProceedsMinor: Long,
        val signedRealizedMinor: Long,
        val remainingQuantity: String
    )

    private data class Lot(var quantity: BigDecimal, var remainingCostMinor: Long)
    private fun qty(value: String): BigDecimal = BigDecimal(value).also {
        require(it > BigDecimal.ZERO) { "Quantity must be positive" }
        require(it.scale() <= 18) { "Quantity exceeds supported precision (18 decimal places)" }
    }

    fun availableQuantity(existing: List<CryptoTrade>, asset: String): BigDecimal {
        val buys = existing.filter { it.asset.equals(asset, true) && it.side == CryptoSide.BUY }
            .sumOf { qty(it.quantity) }
        val sells = existing.filter { it.asset.equals(asset, true) && it.side == CryptoSide.SELL }
            .sumOf { qty(it.quantity) }
        return buys - sells
    }

    /**
     * Replays trades in chronological insertion order (date, createdAt, id).
     * Existing sales consume FIFO lots; new sale must be no earlier than previous trades.
     * Editing/removing old trades requires a full recomputation and is intentionally unsupported.
     */
    fun calculateSale(existing: List<CryptoTrade>, sale: CryptoTrade): SaleResult {
        require(sale.side == CryptoSide.SELL)
        require(sale.grossLkrMinor > 0 && sale.feeLkrMinor >= 0)
        require(sale.grossLkrMinor >= sale.feeLkrMinor) { "Selling fee exceeds proceeds" }
        val sorted = existing.filter { it.asset.equals(sale.asset, true) }
            .sortedWith(compareBy<CryptoTrade>({ it.date }, { it.createdAt }, { it.id }))
        require(sorted.none { it.id == sale.id }) { "Trade ID already exists" }
        require(sorted.isEmpty() || sale.date >= sorted.last().date) {
            "Backdated trades need a complete FIFO revaluation; enter trades chronologically"
        }
        val lots = mutableListOf<Lot>()
        fun consume(amount: BigDecimal): Long {
            var remain = amount
            var allocatedCost = 0L
            for (lot in lots) {
                if (remain.signum() == 0) break
                if (lot.quantity.signum() == 0) continue
                val take = remain.min(lot.quantity)
                val cost = if (take.compareTo(lot.quantity) == 0) lot.remainingCostMinor else
                    BigDecimal.valueOf(lot.remainingCostMinor)
                        .multiply(take).divide(lot.quantity, 0, RoundingMode.HALF_UP).longValueExact()
                allocatedCost = Math.addExact(allocatedCost, cost)
                lot.remainingCostMinor = Math.subtractExact(lot.remainingCostMinor, cost)
                lot.quantity -= take
                remain -= take
            }
            require(remain.signum() == 0) { "Insufficient recorded holdings for this sale" }
            return allocatedCost
        }
        for (trade in sorted) {
            when (trade.side) {
                CryptoSide.BUY -> {
                    require(trade.grossLkrMinor > 0 && trade.feeLkrMinor >= 0)
                    lots += Lot(qty(trade.quantity), Math.addExact(trade.grossLkrMinor, trade.feeLkrMinor))
                }
                CryptoSide.SELL -> consume(qty(trade.quantity))
            }
        }
        val cost = consume(qty(sale.quantity))
        val proceeds = Math.subtractExact(sale.grossLkrMinor, sale.feeLkrMinor)
        return SaleResult(cost, proceeds, Math.subtractExact(proceeds, cost),
            lots.fold(BigDecimal.ZERO) { sum, lot -> sum + lot.quantity }.stripTrailingZeros().toPlainString())
    }
}
