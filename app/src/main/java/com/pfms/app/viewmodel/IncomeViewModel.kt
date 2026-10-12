
package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.AuthRepository
import com.pfms.app.domain.repository.IncomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class IncomeUiState(
    val incomes: List<Income> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saveSuccessful: Boolean = false
)

class IncomeViewModel(
    private val incomeRepository: IncomeRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IncomeUiState())
    val uiState: StateFlow<IncomeUiState> =
        _uiState.asStateFlow()

    init {
        observeIncome()
    }

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
                    _uiState.value = _uiState.value.copy(
                        incomes = records.sortedWith(
                            compareByDescending<Income> {
                                it.receivedDate
                            }.thenByDescending {
                                it.createdAt
                            }
                        ),
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }

    fun addIncome(
        amount: String,
        currency: String,
        source: String,
        receivedDate: String,
        note: String
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
                java.time.LocalDate.parse(receivedDate)
            } catch (e: Exception) {
                showError("Invalid received date.")
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

                val income = Income(
                    id = UUID.randomUUID().toString(),
                    userId = user.uid,
                    amountMinor = amountMinor,
                    currency = currency,
                    source = source,
                    receivedDate = receivedDate,
                    note = note.trim(),
                    amountLkrMinor =
                        if (currency == "LKR") {
                            amountMinor
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

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to save income. Please retry."
                )
            }
        }
    }

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


    fun updateIncome(
        incomeId: String,
        amount: String,
        currency: String,
        source: String,
        receivedDate: String,
        note: String
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
                java.time.LocalDate.parse(receivedDate)
            } catch (e: Exception) {
                showError("Invalid received date.")
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

                val conversionChanged =
                    existingIncome.amountMinor != amountMinor ||
                            existingIncome.currency != currency ||
                            existingIncome.receivedDate != receivedDate

                val updatedIncome = existingIncome.copy(
                    amountMinor = amountMinor,
                    currency = currency,
                    source = source,
                    receivedDate = receivedDate,
                    note = note.trim(),

                    // Avoid retaining an outdated conversion.
                    amountLkrMinor = when {
                        currency == "LKR" -> amountMinor
                        conversionChanged -> null
                        else -> existingIncome.amountLkrMinor
                    },

                    exchangeRate = if (conversionChanged) {
                        null
                    } else {
                        existingIncome.exchangeRate
                    },

                    exchangeRateDate = if (conversionChanged) {
                        null
                    } else {
                        existingIncome.exchangeRateDate
                    },

                    updatedAt = System.currentTimeMillis()
                )

                incomeRepository.updateIncome(updatedIncome)

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccessful = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to update income. Please retry."
                )
            }
        }
    }

}
