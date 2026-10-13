
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

    // Prevent a dismissed reminder from reopening
    // repeatedly during the same ViewModel session.
    private var dismissedMonth: String? = null

    init {
        observeTemplate()
    }

    // -----------------------------------------
    // OBSERVE SALARY TEMPLATE
    // -----------------------------------------

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

                    val today = LocalDate.now(colomboZone)

                    val currentMonth =
                        reminderUseCase.getCurrentMonthKey(today)

                    val shouldPrompt =
                        dismissedMonth != currentMonth &&
                                (template?.let {
                                    reminderUseCase.shouldShowReminder(
                                        template = it,
                                        today = today
                                    )
                                } ?: false)

                    _uiState.value = _uiState.value.copy(
                        template = template,
                        isLoading = false,
                        showConfirmation = shouldPrompt,
                        errorMessage = null
                    )
                }
        }
    }

    // -----------------------------------------
    // REFRESH SALARY REMINDER
    // -----------------------------------------

    /**
     * Rechecks whether the salary reminder should
     * appear based on the current Colombo date.
     *
     * Call this when the app/salary screen resumes.
     */
    fun refreshSalaryReminder() {

        val template = _uiState.value.template

        if (template == null) {
            _uiState.value = _uiState.value.copy(
                showConfirmation = false
            )
            return
        }

        val today = LocalDate.now(colomboZone)

        val currentMonth =
            reminderUseCase.getCurrentMonthKey(today)

        val shouldShow =
            dismissedMonth != currentMonth &&
                    reminderUseCase.shouldShowReminder(
                        template = template,
                        today = today
                    )

        _uiState.value = _uiState.value.copy(
            showConfirmation = shouldShow
        )
    }

    // -----------------------------------------
    // SAVE SALARY TEMPLATE
    // -----------------------------------------

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

                // Repository must enforce ownership
                // using the authenticated user's UID.
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

    // -----------------------------------------
    // CONFIRM MONTHLY SALARY
    // -----------------------------------------

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

        val today = LocalDate.now(colomboZone)

        if (!reminderUseCase.shouldShowReminder(
                template = template,
                today = today
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
                    reminderUseCase.getCurrentMonthKey(today)

                repository.confirmMonthlySalary(
                    templateId = template.id,
                    monthKey = monthKey,
                    amountMinor = amountMinor
                )

                // Close reminder only after the
                // repository confirms success.
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

    // -----------------------------------------
    // DISMISS SALARY CONFIRMATION
    // -----------------------------------------

    fun dismissConfirmation() {

        if (_uiState.value.isSaving) return

        dismissedMonth =
            reminderUseCase.getCurrentMonthKey()

        _uiState.value = _uiState.value.copy(
            showConfirmation = false
        )
    }

    // -----------------------------------------
    // CLEAR SUCCESS
    // -----------------------------------------

    fun clearSuccess() {

        _uiState.value = _uiState.value.copy(
            operationSuccessful = false
        )
    }

    // -----------------------------------------
    // CLEAR ERROR
    // -----------------------------------------

    fun clearError() {

        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    // -----------------------------------------
    // SHOW ERROR
    // -----------------------------------------

    private fun showError(message: String) {

        _uiState.value = _uiState.value.copy(
            errorMessage = message
        )
    }

    // -----------------------------------------
    // VALIDATE AND CONVERT SALARY AMOUNT
    // -----------------------------------------

    /**
     * Converts an amount such as "130000.50"
     * into integer minor units: 13000050.
     *
     * BigDecimal prevents floating-point
     * precision problems.
     */
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
