
package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.SalaryTemplate
import com.pfms.app.domain.repository.SalaryTemplateRepository
import com.pfms.app.domain.usecase.SalaryReminderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class SalaryUiState(
    val template: SalaryTemplate? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val showConfirmation: Boolean = false,
    val errorMessage: String? = null,
    val operationSuccessful: Boolean = false
)

class SalaryViewModel(
    private val repository: SalaryTemplateRepository,
    private val reminderUseCase: SalaryReminderUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalaryUiState())

    val uiState: StateFlow<SalaryUiState> =
        _uiState.asStateFlow()

    private val colomboZone =
        ZoneId.of("Asia/Colombo")

    init {
        observeTemplate()
    }

    private fun observeTemplate() {
        viewModelScope.launch {
            repository.observeSalaryTemplate()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage =
                            "Unable to load salary template."
                    )
                }
                .collect { template ->
                    val shouldPrompt =
                        template?.let {
                            reminderUseCase.shouldShowReminder(
                                it,
                                LocalDate.now(colomboZone)
                            )
                        } ?: false

                    _uiState.value = _uiState.value.copy(
                        template = template,
                        isLoading = false,
                        showConfirmation = shouldPrompt
                    )
                }
        }
    }

    fun saveSalaryTemplate(amount: String) {
        if (_uiState.value.isSaving) return

        val amountMinor = parseAmount(amount)

        if (amountMinor == null) {
            showError("Enter a valid salary amount.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                operationSuccessful = false
            )

            try {
                val existing = _uiState.value.template
                val now = System.currentTimeMillis()

                val template = if (existing == null) {
                    SalaryTemplate(
                        id = UUID.randomUUID().toString(),
                        amountMinor = amountMinor,
                        currency = "LKR",
                        paymentDay = 25,
                        isEnabled = true,
                        createdAt = now,
                        updatedAt = now
                    )
                } else {
                    existing.copy(
                        amountMinor = amountMinor,
                        updatedAt = now
                    )
                }

                // Repository must assign/validate the
                // authenticated user's UID.
                repository.saveSalaryTemplate(template)

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    operationSuccessful = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to save salary template. " +
                                "Please try again."
                )
            }
        }
    }

    fun confirmSalary(amount: String) {
        if (_uiState.value.isSaving) return

        val template = _uiState.value.template

        if (template == null) {
            showError("Salary template not found.")
            return
        }

        val amountMinor = parseAmount(amount)

        if (amountMinor == null) {
            showError("Enter a valid salary amount.")
            return
        }

        if (!reminderUseCase.shouldShowReminder(
                template,
                LocalDate.now(colomboZone)
            )
        ) {
            showError(
                "Salary confirmation is not due " +
                        "or is already completed."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSaving = true,
                errorMessage = null,
                operationSuccessful = false
            )

            try {
                val monthKey =
                    reminderUseCase.getCurrentMonthKey()

                repository.confirmMonthlySalary(
                    templateId = template.id,
                    monthKey = monthKey,
                    amountMinor = amountMinor
                )

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    showConfirmation = false,
                    operationSuccessful = true
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to confirm salary. " +
                                "Please retry."
                )
            }
        }
    }

    fun dismissConfirmation() {
        if (_uiState.value.isSaving) return

        _uiState.value = _uiState.value.copy(
            showConfirmation = false
        )
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(
            operationSuccessful = false
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message
        )
    }

    private fun parseAmount(value: String): Long? {
        return try {
            val text = value.trim()

            if (!text.matches(
                    Regex("""\d+(\.\d{1,2})?""")
                )
            ) {
                return null
            }

            val amount = BigDecimal(text)

            if (amount <= BigDecimal.ZERO) {
                return null
            }

            amount.movePointRight(2)
                .longValueExact()

        } catch (e: Exception) {
            null
        }
    }
}
