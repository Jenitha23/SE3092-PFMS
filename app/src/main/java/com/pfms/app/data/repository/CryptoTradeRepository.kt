package com.pfms.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.CryptoTradeDao
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.entity.CryptoTradeEntity
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.sync.CryptoTradeSyncWorker
import com.pfms.app.data.sync.IncomeSyncWorker
import com.pfms.app.domain.model.CryptoSide
import com.pfms.app.domain.model.CryptoTrade
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.usecase.crypto.CryptoFifoCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private fun CryptoTradeEntity.domain() = CryptoTrade(id,userId,asset,CryptoSide.valueOf(side),quantity,
    grossLkrMinor,feeLkrMinor,date,createdAt,originalCurrency,originalGrossMinor,originalFeeMinor,
    exchangeRate,note,realizedLkrMinor)
private fun CryptoTrade.entity() = CryptoTradeEntity(id,userId,asset,side.name,quantity,grossLkrMinor,
    feeLkrMinor,date,createdAt,originalCurrency,originalGrossMinor,originalFeeMinor,exchangeRate,note,realizedLkrMinor)

/** Local-first inserts for BOTH trade and generated Income are committed atomically. */
@Singleton
class CryptoTradeRepository @Inject constructor(
    private val db: PfmsDatabase,
    private val trades: CryptoTradeDao,
    private val incomes: IncomeDao,
    private val auth: AuthRepository,
    @ApplicationContext private val context: Context
) {
    /**
     * Pass already-converted grossLkrMinor/feeLkrMinor. Caller must use a real FX snapshot
     * for USD input; zero/placeholder FX values are not accepted.
     * Append-only policy: no editing/deleting past trades until recalculation is implemented.
     */
    suspend fun save(input: CryptoTrade): CryptoTrade {
        val uid = auth.currentUser.first()?.uid ?: error("Login required")
        require(uid == input.userId && uid.isNotBlank()) { "Wrong user" }
        val trade = input.copy(asset = input.asset.uppercase().trim())
        require(trade.asset.matches(Regex("[A-Z0-9]{2,15}"))) { "Invalid asset" }
        LocalDate.parse(trade.date)
        require(trade.grossLkrMinor > 0 && trade.feeLkrMinor >= 0)
        require(trade.originalCurrency == "LKR" || trade.originalCurrency == "USD")
        if (trade.originalCurrency == "USD") {
            require(!trade.exchangeRate.isNullOrBlank() && trade.exchangeRate.toBigDecimal() > java.math.BigDecimal.ZERO) {
                "USD trades require a positive exchange-rate snapshot"
            }
        }
        require(trade.id.isNotBlank() && trade.createdAt > 0)
        val saved = db.withTransaction {
            val existing = trades.all(uid).map { it.domain() }
            require(existing.none { it.id == trade.id }) { "This trade already exists" }
            // FIFO permits append-only chronological insertion in this starter implementation.
            val latest = existing.maxOfOrNull { it.date }
            require(latest == null || trade.date >= latest) { "Enter trades chronologically" }
            val result = when (trade.side) {
                CryptoSide.BUY -> {
                    require(trade.grossLkrMinor > 0)
                    trade.copy(realizedLkrMinor = null)
                }
                CryptoSide.SELL -> {
                    val calc = CryptoFifoCalculator.calculateSale(existing, trade)
                    trade.copy(realizedLkrMinor = calc.signedRealizedMinor)
                }
            }
            // Always persist trade; zero P/L is valid but must NOT create 0-amount income
            trades.insert(result.entity())
            val pnl = result.realizedLkrMinor
            if (result.side == CryptoSide.SELL && pnl != null && pnl != 0L) {
                val now = System.currentTimeMillis()
                // Existing dashboards expect non-negative amount + cryptoType Gain/Loss.
                // Stable ID makes queue retries and multiple saves idempotent.
                val incomeId = "crypto-sale-${result.id}"
                require(incomes.getIncomeById(uid, incomeId) == null) { "Income already exists for this sale" }
                incomes.upsertIncome(IncomeEntity(
                    id = incomeId, userId = uid, amountMinor = kotlin.math.abs(pnl),
                    amountLkrMinor = kotlin.math.abs(pnl), currency = "LKR", source = "Crypto",
                    receivedDate = result.date,
                    note = "Automatically calculated from crypto sale ${result.id}",
                    cryptoType = if (pnl < 0L) "Loss" else "Gain",
                    cryptoAsset = result.asset, paymentStatus = "Received", createdAt = now,
                    updatedAt = now, syncStatus = SyncStatus.PENDING_INSERT,
                    revision = UUID.randomUUID().toString()
                ))
            }
            result
        }
        // Existing income sync handles generated income. Dedicated trade worker handles trades.
        IncomeSyncWorker.enqueue(context)
        CryptoTradeSyncWorker.enqueue(context)
        return saved
    }
}
