
package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.local.dao.CryptoTradeDao
import com.pfms.app.data.local.entity.CryptoTradeEntity
import com.pfms.app.data.repository.CryptoTradeRepository
import com.pfms.app.domain.model.CryptoSide
import com.pfms.app.domain.model.CryptoTrade
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.usecase.crypto.CryptoFifoCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class CryptoTradeForm(
    val side: CryptoSide = CryptoSide.BUY,
    val asset: String = "BTC",
    val quantity: String = "",
    val gross: String = "",
    val fee: String = "0",
    val currency: String = "LKR",
    val exchangeRate: String = "",
    val date: String = LocalDate.now(
        ZoneId.of("Asia/Colombo")
    ).toString(),
    val note: String = ""
)

data class CryptoTradeUiState(
    val form: CryptoTradeForm = CryptoTradeForm(),
    val trades: List<CryptoTrade> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CryptoTradingViewModel @Inject constructor(
    private val repository: CryptoTradeRepository,
    private val dao: CryptoTradeDao,
    private val auth: AuthRepository
) : ViewModel() {

    private val mutable = MutableStateFlow(
        CryptoTradeUiState()
    )

    val state: StateFlow<CryptoTradeUiState> =
        mutable.asStateFlow()

    init {
        observeCryptoTrades()
    }

    /**
     * Observe cryptocurrency trades for the
     * currently authenticated user.
     */
    private fun observeCryptoTrades() {
        viewModelScope.launch {
            auth.currentUser
                .flatMapLatest { user ->

                    if (user == null) {
                        flowOf(emptyList<CryptoTrade>())
                    } else {
                        dao.observe(user.uid)
                            .map { rows ->
                                rows.map(::toDomain)
                            }
                    }
                }
                .catch { exception ->

                    // FIX: exception is the Throwable,
                    // not the current UI state.
                    mutable.update { currentState ->
                        currentState.copy(
                            loading = false,
                            error = "Could not load trades: " +
                                    (exception.message ?: "Unknown error")
                        )
                    }
                }
                .collect { trades ->

                    mutable.update { currentState ->
                        currentState.copy(
                            trades = trades,
                            loading = false
                        )
                    }
                }
        }
    }

    /**
     * Update the trade form.
     */
    fun edit(
        change: (CryptoTradeForm) -> CryptoTradeForm
    ) {
        mutable.update { currentState ->

            if (currentState.saving) {
                currentState
            } else {
                currentState.copy(
                    form = change(currentState.form),
                    error = null,
                    saved = false
                )
            }
        }
    }

    /**
     * Reset the success state after the UI
     * handles a successful save.
     */
    fun clearSuccess() {
        mutable.update { currentState ->
            currentState.copy(saved = false)
        }
    }

    /**
     * Returns available cryptocurrency quantity
     * calculated using existing trade records.
     */
    fun available(): String {

        return CryptoFifoCalculator.availableQuantity(
            state.value.trades,
            state.value.form.asset
        )
            .stripTrailingZeros()
            .toPlainString()
    }

    /**
     * Preview the calculated realized gain/loss.
     *
     * This is only a preview.
     * The repository must recheck FIFO calculations
     * when saving an actual SELL transaction.
     */
    fun preview(): CryptoFifoCalculator.SaleResult? {

        return runCatching {

            val form = state.value.form

            if (form.side != CryptoSide.SELL) {
                null
            } else {

                val trade = buildTrade(
                    f = form,
                    userId = "preview",
                    id = "preview",
                    createdAt = System.currentTimeMillis()
                )

                CryptoFifoCalculator.calculateSale(
                    existing = state.value.trades,
                    sale = trade
                )
            }

        }.getOrNull()
    }

    /**
     * Save a new BUY or SELL trade.
     *
     * The repository handles persistence
     * and links realized SELL gains/losses
     * to the existing income system.
     */
    fun save() {

        if (state.value.saving) return

        val formSnapshot = state.value.form

        mutable.update { currentState ->
            currentState.copy(
                saving = true,
                error = null,
                saved = false
            )
        }

        viewModelScope.launch {

            try {

                val uid = auth.currentUser
                    .first()
                    ?.uid
                    ?: error("Please sign in again")

                val trade = buildTrade(
                    f = formSnapshot,
                    userId = uid,
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis()
                )

                repository.save(trade)

                mutable.update { currentState ->
                    currentState.copy(
                        saving = false,
                        saved = true,
                        form = CryptoTradeForm(
                            side = formSnapshot.side
                        )
                    )
                }

            } catch (exception: Exception) {

                mutable.update { currentState ->
                    currentState.copy(
                        saving = false,
                        saved = false,
                        error = exception.message
                            ?: "Could not save trade"
                    )
                }
            }
        }
    }

    /**
     * Validate the form and construct
     * the cryptocurrency trade model.
     */
    private fun buildTrade(
        f: CryptoTradeForm,
        userId: String,
        id: String,
        createdAt: Long
    ): CryptoTrade {

        val quantity = f.quantity
            .trim()
            .toBigDecimal()

        require(
            quantity > BigDecimal.ZERO &&
                    quantity.scale() <= 18
        ) {
            "Enter a valid quantity (up to 18 decimals)"
        }

        val gross = minor(f.gross)

        val fee = minor(
            f.fee.ifBlank { "0" }
        )

        require(gross > 0) {
            "Enter a trade value greater than zero"
        }

        require(fee >= 0) {
            "Trading fee cannot be negative"
        }

        if (f.side == CryptoSide.SELL) {
            require(gross >= fee) {
                "Selling fee cannot exceed proceeds"
            }
        }

        val currency = f.currency
            .trim()
            .uppercase()

        require(
            currency in setOf("LKR", "USD")
        ) {
            "Select LKR or USD"
        }

        val rate = if (currency == "USD") {

            f.exchangeRate
                .trim()
                .toBigDecimal()
                .also { parsedRate ->

                    require(
                        parsedRate > BigDecimal.ZERO
                    ) {
                        "Enter a valid USD to LKR exchange rate"
                    }
                }

        } else {
            BigDecimal.ONE
        }

        val asset = f.asset
            .trim()
            .uppercase()

        require(
            asset.matches(
                Regex("[A-Z0-9]{2,15}")
            )
        ) {
            "Enter a valid asset symbol"
        }

        val date = LocalDate.parse(
            f.date.trim()
        )

        val today = LocalDate.now(
            ZoneId.of("Asia/Colombo")
        )

        require(!date.isAfter(today)) {
            "Transaction date cannot be in the future"
        }

        /**
         * Convert monetary values to LKR minor units.
         * HALF_UP rounding prevents floating-point errors.
         */
        fun convert(value: Long): Long {

            return BigDecimal.valueOf(value)
                .multiply(rate)
                .setScale(
                    0,
                    RoundingMode.HALF_UP
                )
                .longValueExact()
        }

        return CryptoTrade(
            id = id,
            userId = userId,
            asset = asset,
            side = f.side,
            quantity = quantity
                .stripTrailingZeros()
                .toPlainString(),
            grossLkrMinor = convert(gross),
            feeLkrMinor = convert(fee),
            date = date.toString(),
            createdAt = createdAt,
            originalCurrency = currency,
            originalGrossMinor = gross,
            originalFeeMinor = fee,
            exchangeRate = if (currency == "USD") {
                rate.toPlainString()
            } else {
                null
            },
            note = f.note.trim()
        )
    }

    /**
     * Convert LKR or USD amounts into minor units.
     *
     * Example:
     * 1500.50 -> 150050
     */
    private fun minor(
        value: String
    ): Long {

        val amount = value
            .trim()
            .toBigDecimal()

        require(amount.signum() >= 0) {
            "Amount cannot be negative"
        }

        return amount
            .movePointRight(2)
            .setScale(
                0,
                RoundingMode.HALF_UP
            )
            .longValueExact()
    }

    /**
     * Convert a Room entity to the
     * domain cryptocurrency model.
     */
    private fun toDomain(
        row: CryptoTradeEntity
    ): CryptoTrade {

        return CryptoTrade(
            id = row.id,
            userId = row.userId,
            asset = row.asset,
            side = CryptoSide.valueOf(row.side),
            quantity = row.quantity,
            grossLkrMinor = row.grossLkrMinor,
            feeLkrMinor = row.feeLkrMinor,
            date = row.date,
            createdAt = row.createdAt,
            originalCurrency = row.originalCurrency,
            originalGrossMinor = row.originalGrossMinor,
            originalFeeMinor = row.originalFeeMinor,
            exchangeRate = row.exchangeRate,
            note = row.note,
            realizedLkrMinor = row.realizedLkrMinor
        )
    }
}
