
package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.data.exchange.ExchangeRateResult
import com.pfms.app.data.exchange.ExchangeRateService
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import com.pfms.app.domain.usecase.CurrencyConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// ─────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────

data class IncomeUiState(
    val incomes: List<Income> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccessful: Boolean = false,

    // Member 2: Exchange-rate state for the currency conversion UI.
    val exchangeRate: BigDecimal? = null,
    val exchangeRateDate: String? = null,
    val isRateFetching: Boolean = false,
    val rateFetchError: String? = null,
    val isManualRateMode: Boolean = false,

    // Member 2: Realized income (excluding unpaid freelance, including crypto P&L).
    val totalRealizedIncomeMinor: Long = 0L,

    // Member 2: Freelance summary.
    val totalPendingFreelanceMinor: Long = 0L,
    val pendingFreelanceCount: Int = 0,

    // Member 2: Crypto summary.
    val cryptoGainsMinor: Long = 0L,
    val cryptoLossesMinor: Long = 0L,
    val netCryptoMinor: Long = 0L,
    val ytdCryptoGainsMinor: Long = 0L,
    val ytdCryptoLossesMinor: Long = 0L,
    val ytdNetCryptoMinor: Long = 0L
)

class IncomeViewModel(
    private val incomeRepository: IncomeRepository,
    private val authRepository: AuthRepository,
    private val exchangeRateService: ExchangeRateService
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeUiState())
    val uiState: StateFlow<IncomeUiState> =
        _uiState.asStateFlow()

    init {
        observeIncome()
    }

    // ─────────────────────────────────────────────────────
    // Observe all income records (Member 1 – preserved).
    // ─────────────────────────────────────────────────────

    private fun observeIncome() {
        viewModelScope.launch {
            incomeRepository.observeIncome()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage =
                            "Unable to load income records."
                    )
                }
                .collect { records ->
                    val sorted = records.sortedWith(
                        compareByDescending<Income> {
                            it.receivedDate
                        }.thenByDescending {
                            it.createdAt
                        }
                    )
                    _uiState.value = _uiState.value.copy(
                        incomes = sorted,
                        isLoading = false,
                        errorMessage = null
                    )
                    // Recalculate Member 2 summaries whenever data changes.
                    recalculateRealizedIncome(sorted)
                    recalculateFreelanceSummary(sorted)
                    recalculateCryptoSummary(sorted)
                }
        }
    }

    // ═════════════════════════════════════════════════════
    // MEMBER 2 – PHASE 1: CURRENCY CONVERSION (FR-07)
    // ═════════════════════════════════════════════════════

    /**
     * Fetch the exchange rate for the given date. If the date is today
     * or in the future, fetch the latest rate. Otherwise fetch the
     * historical rate for backdated entries (SRS requirement).
     */
    fun fetchExchangeRate(receivedDate: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRateFetching = true,
                rateFetchError = null,
                isManualRateMode = false
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    val entryDate = LocalDate.parse(receivedDate)
                    val today = LocalDate.now(
                        ZoneId.of("Asia/Colombo")
                    )

                    if (entryDate >= today) {
                        exchangeRateService.fetchLatestRate()
                    } else {
                        // Backdated entry → historical rate only.
                        // If historical fails, force manual mode
                        // instead of silently using today's rate.
                        val historical =
                            exchangeRateService.fetchHistoricalRate(
                                receivedDate
                            )
                        if (historical is ExchangeRateResult.Failure) {
                            ExchangeRateResult.Failure(
                                "Historical rate for $receivedDate " +
                                "is unavailable. Please enter " +
                                "the rate manually."
                            )
                        } else {
                            historical
                        }
                    }
                } catch (e: Exception) {
                    ExchangeRateResult.Failure(
                        "Invalid date format. Please enter " +
                        "the rate manually."
                    )
                }
            }

            when (result) {
                is ExchangeRateResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        exchangeRate = result.rate,
                        exchangeRateDate = result.rateDate,
                        isRateFetching = false,
                        rateFetchError = null,
                        isManualRateMode = false
                    )
                }
                is ExchangeRateResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isRateFetching = false,
                        rateFetchError = result.message,
                        isManualRateMode = true
                    )
                }
            }
        }
    }

    /**
     * Manually set the exchange rate when the API is unavailable.
     * Validates that the rate is a positive number.
     */
    fun setManualExchangeRate(rateString: String): Boolean {
        return try {
            val rate = BigDecimal(rateString.trim())
            if (rate <= BigDecimal.ZERO) {
                showError(
                    "Exchange rate must be a positive number."
                )
                return false
            }
            _uiState.value = _uiState.value.copy(
                exchangeRate = rate,
                exchangeRateDate = LocalDate.now(
                    ZoneId.of("Asia/Colombo")
                ).toString(),
                rateFetchError = null,
                isManualRateMode = true
            )
            true
        } catch (e: Exception) {
            showError("Enter a valid exchange rate.")
            false
        }
    }

    /** Clear exchange rate state (e.g. when user switches from USD to LKR). */
    fun clearExchangeRate() {
        _uiState.value = _uiState.value.copy(
            exchangeRate = null,
            exchangeRateDate = null,
            isRateFetching = false,
            rateFetchError = null,
            isManualRateMode = false
        )
    }

    // ═════════════════════════════════════════════════════
    // ADD INCOME (Member 1 base + Member 2 extensions)
    // ═════════════════════════════════════════════════════

    fun addIncome(
        amount: String,
        currency: String,
        source: String,
        receivedDate: String,
        note: String,
        // Member 2 – Freelance fields (FR-08).
        projectName: String? = null,
        paymentStatus: String = "Received",
        // Member 2 – Crypto fields (FR-09).
        cryptoType: String? = null,
        cryptoAsset: String? = null
    ) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            val amountMinor = parseAmount(amount)

            if (amountMinor == null) {
                showError("Enter a valid positive amount.")
                return@launch
            }

            if (currency !in listOf("LKR", "USD")) {
                showError("Invalid currency.")
                return@launch
            }

            if (source !in listOf(
                    "Salary",
                    "Freelance",
                    "Google AdSense",
                    "Crypto",
                    "Other"
                )
            ) {
                showError("Invalid income source.")
                return@launch
            }

            try {
                LocalDate.parse(receivedDate)
            } catch (e: Exception) {
                showError("Invalid received date.")
                return@launch
            }

            // Member 2: Freelance validation – project name required.
            if (source == "Freelance" &&
                projectName.isNullOrBlank()
            ) {
                showError(
                    "Project/client name is required " +
                            "for freelance income."
                )
                return@launch
            }

            // Member 2: Crypto validation – type required.
            if (source == "Crypto" &&
                cryptoType !in listOf("Gain", "Loss")
            ) {
                showError(
                    "Select Gain or Loss for " +
                            "cryptocurrency income."
                )
                return@launch
            }

            // Member 2: USD conversion – exchange rate required.
            if (currency == "USD" &&
                _uiState.value.exchangeRate == null
            ) {
                showError(
                    "Exchange rate is required for " +
                            "USD income. Fetch the rate or " +
                            "enter it manually."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                saveSuccessful = false
            )

            try {
                val user = authRepository.currentUser.first()
                    ?: error("Authentication required")

                val now = System.currentTimeMillis()

                // Member 2: Calculate LKR equivalent for USD entries.
                val exchangeRate = _uiState.value.exchangeRate
                val amountLkrMinor: Long?
                val storedRate: String?
                val storedRateDate: String?

                if (currency == "USD" && exchangeRate != null) {
                    amountLkrMinor =
                        CurrencyConverter.convertUsdToLkrMinor(
                            amountMinor,
                            exchangeRate
                        )
                    storedRate = exchangeRate.toPlainString()
                    storedRateDate =
                        _uiState.value.exchangeRateDate
                } else if (currency == "LKR") {
                    amountLkrMinor = amountMinor
                    storedRate = null
                    storedRateDate = null
                } else {
                    amountLkrMinor = null
                    storedRate = null
                    storedRateDate = null
                }

                val income = Income(
                    id = UUID.randomUUID().toString(),
                    userId = user.uid,
                    amountMinor = amountMinor,
                    currency = currency,
                    source = source,
                    receivedDate = receivedDate,
                    note = note.trim(),
                    // Member 2: Permanent currency conversion fields.
                    amountLkrMinor = amountLkrMinor,
                    exchangeRate = storedRate,
                    exchangeRateDate = storedRateDate,
                    // Member 2: Freelance fields.
                    paymentStatus = if (source == "Freelance") {
                        paymentStatus
                    } else {
                        "Received"
                    },
                    projectName = if (source == "Freelance") {
                        projectName?.trim()
                    } else {
                        null
                    },
                    // Member 2: Crypto fields.
                    cryptoType = if (source == "Crypto") {
                        cryptoType
                    } else {
                        null
                    },
                    cryptoAsset = if (source == "Crypto") {
                        cryptoAsset?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    },
                    createdAt = now,
                    updatedAt = now
                )

                incomeRepository.addIncome(income)

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccessful = true
                )
                // Clear exchange rate after successful save.
                clearExchangeRate()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to save income. Please retry."
                )
            }
        }
    }

    // ═════════════════════════════════════════════════════
    // UPDATE INCOME (Member 1 base + Member 2 extensions)
    // ═════════════════════════════════════════════════════

    fun updateIncome(
        incomeId: String,
        amount: String,
        currency: String,
        source: String,
        receivedDate: String,
        note: String,
        // Member 2 – Freelance fields (FR-08).
        projectName: String? = null,
        paymentStatus: String = "Received",
        // Member 2 – Crypto fields (FR-09).
        cryptoType: String? = null,
        cryptoAsset: String? = null
    ) {
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            val amountMinor = parseAmount(amount)

            if (amountMinor == null) {
                showError("Enter a valid positive amount.")
                return@launch
            }

            if (currency !in listOf("LKR", "USD")) {
                showError("Invalid currency.")
                return@launch
            }

            if (source !in listOf(
                    "Salary",
                    "Freelance",
                    "Google AdSense",
                    "Crypto",
                    "Other"
                )
            ) {
                showError("Invalid income source.")
                return@launch
            }

            try {
                LocalDate.parse(receivedDate)
            } catch (e: Exception) {
                showError("Invalid received date.")
                return@launch
            }

            // Member 2: Freelance validation.
            if (source == "Freelance" &&
                projectName.isNullOrBlank()
            ) {
                showError(
                    "Project/client name is required " +
                            "for freelance income."
                )
                return@launch
            }

            // Member 2: Crypto validation.
            if (source == "Crypto" &&
                cryptoType !in listOf("Gain", "Loss")
            ) {
                showError(
                    "Select Gain or Loss for " +
                            "cryptocurrency income."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                saveSuccessful = false
            )

            try {
                val user = authRepository.currentUser.first()
                    ?: error("Authentication required")

                val existingIncome =
                    incomeRepository.getIncomeById(incomeId)
                        ?: error("Income record not found")

                if (existingIncome.userId != user.uid) {
                    error("Access denied")
                }

                // Detect if conversion inputs changed.
                val conversionChanged =
                    existingIncome.amountMinor != amountMinor ||
                            existingIncome.currency != currency ||
                            existingIncome.receivedDate != receivedDate

                // Member 2: Resolve exchange rate & LKR amount.
                val exchangeRate = _uiState.value.exchangeRate
                val amountLkrMinor: Long?
                val storedRate: String?
                val storedRateDate: String?

                when {
                    currency == "LKR" -> {
                        amountLkrMinor = amountMinor
                        storedRate = null
                        storedRateDate = null
                    }
                    // New rate available from UI → use it.
                    currency == "USD" && exchangeRate != null -> {
                        amountLkrMinor =
                            CurrencyConverter.convertUsdToLkrMinor(
                                amountMinor,
                                exchangeRate
                            )
                        storedRate = exchangeRate.toPlainString()
                        storedRateDate =
                            _uiState.value.exchangeRateDate
                    }
                    // Nothing changed → keep the existing stored rate.
                    !conversionChanged -> {
                        amountLkrMinor =
                            existingIncome.amountLkrMinor
                        storedRate = existingIncome.exchangeRate
                        storedRateDate =
                            existingIncome.exchangeRateDate
                    }
                    // Conversion changed but no new rate → null out.
                    else -> {
                        amountLkrMinor = null
                        storedRate = null
                        storedRateDate = null
                    }
                }

                val updatedIncome = existingIncome.copy(
                    amountMinor = amountMinor,
                    currency = currency,
                    source = source,
                    receivedDate = receivedDate,
                    note = note.trim(),
                    // Member 2: Permanent conversion fields.
                    amountLkrMinor = amountLkrMinor,
                    exchangeRate = storedRate,
                    exchangeRateDate = storedRateDate,
                    // Member 2: Freelance fields.
                    paymentStatus = if (source == "Freelance") {
                        paymentStatus
                    } else {
                        "Received"
                    },
                    projectName = if (source == "Freelance") {
                        projectName?.trim()
                    } else {
                        null
                    },
                    // Member 2: Crypto fields.
                    cryptoType = if (source == "Crypto") {
                        cryptoType
                    } else {
                        null
                    },
                    cryptoAsset = if (source == "Crypto") {
                        cryptoAsset?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    },
                    updatedAt = System.currentTimeMillis()
                )

                incomeRepository.updateIncome(updatedIncome)

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccessful = true
                )
                clearExchangeRate()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to update income. Please retry."
                )
            }
        }
    }

    // ═════════════════════════════════════════════════════
    // MEMBER 2 – PHASE 3: FREELANCE SUMMARY (FR-08)
    // ═════════════════════════════════════════════════════

    /**
     * Mark a freelance invoice as "Received" so it counts
     * toward actual income totals.
     */
    fun markFreelanceAsReceived(incomeId: String) {
        viewModelScope.launch {
            try {
                val income =
                    incomeRepository.getIncomeById(incomeId)
                        ?: return@launch
                if (income.source == "Freelance" &&
                    income.paymentStatus == "Expected"
                ) {
                    incomeRepository.updateIncome(
                        income.copy(
                            paymentStatus = "Received",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                showError(
                    "Unable to update payment status."
                )
            }
        }
    }

    /**
     * Returns the LKR-equivalent amount for totals.
     * For LKR entries, returns amountMinor.
     * For USD entries with a stored conversion, returns amountLkrMinor.
     * For USD entries WITHOUT a stored conversion, returns 0L
     * (prevents treating raw USD cents as LKR cents).
     */
    private fun Income.lkrAmount(): Long {
        return when {
            currency == "LKR" -> amountMinor
            amountLkrMinor != null -> amountLkrMinor
            else -> 0L  // Unconverted USD – exclude from LKR totals.
        }
    }

    /**
     * Calculates realized net income:
     *   (non-crypto received income) + crypto gains – crypto losses.
     * Excludes unpaid freelance invoices (paymentStatus == "Expected").
     */
    private fun recalculateRealizedIncome(
        records: List<Income>
    ) {
        val nonCryptoRealized = records.filter {
            if (it.source == "Freelance") {
                it.paymentStatus == "Received"
            } else {
                it.source != "Crypto"
            }
        }.sumOf { it.lkrAmount() }

        val cryptoGains = records.filter {
            it.source == "Crypto" && it.cryptoType == "Gain"
        }.sumOf { it.lkrAmount() }

        val cryptoLosses = records.filter {
            it.source == "Crypto" && it.cryptoType == "Loss"
        }.sumOf { it.lkrAmount() }

        _uiState.value = _uiState.value.copy(
            totalRealizedIncomeMinor =
                nonCryptoRealized + cryptoGains - cryptoLosses
        )
    }

    private fun recalculateFreelanceSummary(
        records: List<Income>
    ) {
        val pending = records.filter {
            it.source == "Freelance" &&
                    it.paymentStatus == "Expected"
        }
        val totalPending = pending.sumOf { it.lkrAmount() }
        _uiState.value = _uiState.value.copy(
            totalPendingFreelanceMinor = totalPending,
            pendingFreelanceCount = pending.size
        )
    }

    // ═════════════════════════════════════════════════════
    // MEMBER 2 – PHASE 3: CRYPTO SUMMARY (FR-09)
    // ═════════════════════════════════════════════════════

    private fun recalculateCryptoSummary(
        records: List<Income>
    ) {
        val cryptoRecords = records.filter {
            it.source == "Crypto"
        }
        val gains = cryptoRecords.filter {
            it.cryptoType == "Gain"
        }.sumOf { it.lkrAmount() }
        val losses = cryptoRecords.filter {
            it.cryptoType == "Loss"
        }.sumOf { it.lkrAmount() }

        // Year-to-date (YTD) calculation.
        val currentYear = LocalDate.now(
            ZoneId.of("Asia/Colombo")
        ).year.toString()

        val ytdRecords = cryptoRecords.filter {
            it.receivedDate.startsWith(currentYear)
        }
        val ytdGains = ytdRecords.filter {
            it.cryptoType == "Gain"
        }.sumOf { it.lkrAmount() }
        val ytdLosses = ytdRecords.filter {
            it.cryptoType == "Loss"
        }.sumOf { it.lkrAmount() }

        _uiState.value = _uiState.value.copy(
            cryptoGainsMinor = gains,
            cryptoLossesMinor = losses,
            netCryptoMinor = gains - losses,
            ytdCryptoGainsMinor = ytdGains,
            ytdCryptoLossesMinor = ytdLosses,
            ytdNetCryptoMinor = ytdGains - ytdLosses
        )
    }

    // ═════════════════════════════════════════════════════
    // DELETE / UTILITY (Member 1 – preserved)
    // ═════════════════════════════════════════════════════

    fun deleteIncome(incomeId: String) {
        viewModelScope.launch {
            try {
                incomeRepository.deleteIncome(incomeId)
            } catch (e: Exception) {
                showError(
                    "Unable to delete income. Please retry."
                )
            }
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(
            saveSuccessful = false
        )
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message
        )
    }

    private fun parseAmount(value: String): Long? {
        return try {
            val amount = BigDecimal(value.trim())

            if (amount <= BigDecimal.ZERO ||
                amount.scale() > 2
            ) {
                return null
            }

            amount.multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: Exception) {
            null
        }
    }

}
