
package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.pfms.app.domain.usecase.IncomeSummaryCalculator
import kotlinx.coroutines.Job
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

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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
    val monthlyRealizedIncomeMinor: Long = 0L,

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

@HiltViewModel
class IncomeViewModel @Inject constructor(
    private val incomeRepository: IncomeRepository,
    private val authRepository: AuthRepository,
    private val exchangeRateService: ExchangeRateService
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeUiState())
    val uiState: StateFlow<IncomeUiState> =
        _uiState.asStateFlow()

    private var rateJob: Job? = null
    private var rateForEntryDate: String? = null

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
                    val summary = IncomeSummaryCalculator.calculate(
                        sorted, LocalDate.now(ZoneId.of("Asia/Colombo"))
                    )
                    _uiState.value = _uiState.value.copy(
                        totalRealizedIncomeMinor = summary.realizedMinor,
                        monthlyRealizedIncomeMinor = summary.monthlyRealizedMinor,
                        totalPendingFreelanceMinor = summary.pendingFreelanceMinor,
                        pendingFreelanceCount = summary.pendingFreelanceCount,
                        cryptoGainsMinor = summary.cryptoGainsMinor,
                        cryptoLossesMinor = summary.cryptoLossesMinor,
                        netCryptoMinor = summary.netCryptoMinor,
                        ytdCryptoGainsMinor = summary.ytdCryptoGainsMinor,
                        ytdCryptoLossesMinor = summary.ytdCryptoLossesMinor,
                        ytdNetCryptoMinor = summary.ytdNetCryptoMinor
                    )
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
        clearExchangeRate()
        rateForEntryDate = receivedDate
        rateJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRateFetching = true)
            val result = withContext(Dispatchers.IO) {
                try {
                    val entry = LocalDate.parse(receivedDate)
                    val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
                    when {
                        entry.isAfter(today) -> ExchangeRateResult.Failure(
                            "A future exchange rate is unavailable. Enter an estimated manual rate."
                        )
                        entry.isBefore(today) -> exchangeRateService.fetchHistoricalRate(receivedDate)
                        else -> exchangeRateService.fetchLatestRate()
                    }
                } catch (_: Exception) {
                    ExchangeRateResult.Failure("Unable to retrieve this date's rate. Enter it manually.")
                }
            }
            // A change to date/currency invalidates requests already in progress.
            if (rateForEntryDate != receivedDate) return@launch
            when (result) {
                is ExchangeRateResult.Success -> {
                    val validDate = runCatching { LocalDate.parse(result.rateDate) }.getOrNull()
                    val entry = runCatching { LocalDate.parse(receivedDate) }.getOrNull()
                    val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
                    // Never accept a historical response from a different date.
                    if (result.rate <= BigDecimal.ZERO || (entry != null && entry < today && validDate != entry)) {
                        _uiState.value = _uiState.value.copy(
                            exchangeRate = null, exchangeRateDate = null,
                            isRateFetching = false, isManualRateMode = true,
                            rateFetchError = "Exact historical rate unavailable. Enter a manual rate."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            exchangeRate = result.rate,
                            exchangeRateDate = result.rateDate,
                            isRateFetching = false, rateFetchError = null, isManualRateMode = false
                        )
                    }
                }
                is ExchangeRateResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        exchangeRate = null, exchangeRateDate = null,
                        isRateFetching = false, rateFetchError = result.message,
                        isManualRateMode = true
                    )
                }
            }
        }
    }

    fun enableManualRate() {
        _uiState.value = _uiState.value.copy(isManualRateMode = true)
    }

    /** A manual rate is associated with the income's selected date, never today's date. */
    fun setManualExchangeRate(rateString: String, receivedDate: String): Boolean {
        val rate = rateString.trim().toBigDecimalOrNull()
        if (rate == null || rate <= BigDecimal.ZERO ||
            runCatching { LocalDate.parse(receivedDate) }.isFailure) {
            showError("Enter a valid positive exchange rate and date.")
            return false
        }
        rateJob?.cancel()
        rateForEntryDate = receivedDate
        _uiState.value = _uiState.value.copy(
            exchangeRate = rate, exchangeRateDate = receivedDate,
            rateFetchError = null, isRateFetching = false, isManualRateMode = true,
            errorMessage = null
        )
        return true
    }

    /** Show an existing conversion when editing, without fetching a new rate. */
    fun loadExistingRate(income: Income) {
        clearExchangeRate()
        if (income.currency == "USD") {
            val rate = income.exchangeRate?.toBigDecimalOrNull()
            if (rate != null && rate > BigDecimal.ZERO) {
                rateForEntryDate = income.receivedDate
                _uiState.value = _uiState.value.copy(
                    exchangeRate = rate, exchangeRateDate = income.exchangeRateDate,
                    isManualRateMode = false
                )
            }
        }
    }

    fun clearExchangeRate() {
        rateJob?.cancel()
        rateForEntryDate = null
        _uiState.value = _uiState.value.copy(
            exchangeRate = null, exchangeRateDate = null,
            isRateFetching = false, rateFetchError = null, isManualRateMode = false
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

            if (source == "Freelance" && paymentStatus !in listOf("Expected", "Received")) {
                showError("Invalid freelance payment status.")
                return@launch
            }

            // Member 2: USD conversion – exchange rate required.
            if (currency == "USD" &&
                (_uiState.value.exchangeRate == null ||
                    rateForEntryDate != receivedDate || _uiState.value.isRateFetching)
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

                // Reuse the original rate for amount-only edits; require a valid
                // selected-date rate when currency/date changes. Preserve all fields
                // for note-only/status-only edits.
                val dateChanged = existingIncome.receivedDate != receivedDate
                val currencyChanged = existingIncome.currency != currency
                val sameConversion = existingIncome.amountMinor == amountMinor &&
                    !dateChanged && !currencyChanged
                val selectedRate = _uiState.value.exchangeRate
                    ?.takeIf { rateForEntryDate == receivedDate && !_uiState.value.isRateFetching }
                val savedRate = existingIncome.exchangeRate?.toBigDecimalOrNull()
                    ?.takeIf { it > BigDecimal.ZERO && !dateChanged && !currencyChanged }

                val amountLkrMinor: Long?
                val storedRate: String?
                val storedRateDate: String?
                when {
                    currency == "LKR" -> {
                        amountLkrMinor = amountMinor
                        storedRate = null
                        storedRateDate = null
                    }
                    currency == "USD" -> {
                        val rate = selectedRate ?: savedRate
                        if (rate == null || rate <= BigDecimal.ZERO) {
                            showError("Fetch or enter a rate for the selected income date before saving.")
                            _uiState.value = _uiState.value.copy(isSaving = false)
                            return@launch
                        }
                        val changedRate = selectedRate != null &&
                            (selectedRate.compareTo(savedRate ?: BigDecimal.ZERO) != 0 ||
                                _uiState.value.exchangeRateDate != existingIncome.exchangeRateDate)
                        if (sameConversion && !changedRate && existingIncome.amountLkrMinor != null) {
                            amountLkrMinor = existingIncome.amountLkrMinor
                            storedRate = existingIncome.exchangeRate
                            storedRateDate = existingIncome.exchangeRateDate
                        } else {
                            amountLkrMinor = CurrencyConverter.convertUsdToLkrMinor(amountMinor, rate)
                            storedRate = rate.toPlainString()
                            storedRateDate = if (selectedRate != null)
                                _uiState.value.exchangeRateDate ?: receivedDate
                            else existingIncome.exchangeRateDate
                        }
                    }
                    else -> error("Unsupported currency")
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
                val uid = authRepository.currentUser.first()?.uid ?: return@launch
                if (income.userId == uid && income.source == "Freelance" &&
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

    fun clearFormError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
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
