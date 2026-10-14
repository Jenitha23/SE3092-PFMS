
package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.repository.ExpenseRepository
import com.pfms.app.domain.usecase.ExpenseRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// Form input received from the Expense UI.
// categoryDefault comes from Member 2's category data.
data class ExpenseFormData(
    val amount: String,
    val categoryId: String,
    val categoryName: String,
    val categoryDefault: ExpenseClassification,
    val classificationOverride: ExpenseClassification? = null,
    val paymentMethod: ExpensePaymentMethod =
        ExpensePaymentMethod.CASH,
    val date: String = LocalDate.now(
        ZoneId.of("Asia/Colombo")
    ).toString(),
    val merchant: String = "",
    val note: String = ""
)

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val pendingDuplicate: Expense? = null,
    val errorMessage: String? = null,
    val operationSuccessful: Boolean = false,
    val lastUsedPaymentMethod: ExpensePaymentMethod =
        ExpensePaymentMethod.CASH
)

class ExpenseViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(ExpenseUiState())

    val uiState: StateFlow<ExpenseUiState> =
        _uiState.asStateFlow()

    private val colomboZone =
        ZoneId.of("Asia/Colombo")

    init {
        observeExpenses()
    }

    // -----------------------------------------
    // OBSERVE EXPENSE RECORDS
    // -----------------------------------------

    private fun observeExpenses() {
        viewModelScope.launch {
            repository.observeExpenses()
                .catch {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage =
                            "Unable to load expenses. Please retry."
                    )
                }
                .collect { records ->
                    _uiState.value = _uiState.value.copy(
                        expenses = records.sortedWith(
                            compareByDescending<Expense> {
                                it.date
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

    // -----------------------------------------
    // QUICK-ADD EXPENSE
    // -----------------------------------------

    /**
     * Quick Add requires only amount and category.
     * Other fields receive default values.
     */
    fun quickAddExpense(
        amount: String,
        categoryId: String,
        categoryName: String,
        categoryDefault: ExpenseClassification
    ) {
        val input = ExpenseFormData(
            amount = amount,
            categoryId = categoryId,
            categoryName = categoryName,
            categoryDefault = categoryDefault,
            paymentMethod =
                _uiState.value.lastUsedPaymentMethod,
            date = LocalDate.now(colomboZone).toString()
        )

        addExpense(input)
    }

    // -----------------------------------------
    // ADD EXPENSE
    // -----------------------------------------

    fun addExpense(input: ExpenseFormData) {

        // Prevent repeated Save taps, including
        // while awaiting duplicate confirmation.
        if (_uiState.value.isSaving ||
            _uiState.value.isDeleting ||
            _uiState.value.pendingDuplicate != null
        ) return

        val amountMinor =
            ExpenseRules.parseAmount(input.amount)

        if (amountMinor == null) {
            showError("Enter a valid positive amount.")
            return
        }

        val validationError =
            ExpenseRules.validateExpense(
                amountMinor = amountMinor,
                categoryId = input.categoryId,
                date = input.date
            )

        if (validationError != null) {
            showError(validationError)
            return
        }

        val classification =
            ExpenseRules.resolveClassification(
                categoryDefault = input.categoryDefault,
                userOverride = input.classificationOverride
            )

        val now = System.currentTimeMillis()

        val expense = Expense(
            id = UUID.randomUUID().toString(),

            // Repository must derive and enforce
            // ownership using Firebase Auth.
            userId = "",

            amountMinor = amountMinor,
            currency = "LKR",
            categoryId = input.categoryId,
            categoryName = input.categoryName,
            classification = classification,
            paymentMethod = input.paymentMethod,
            date = input.date,
            merchant = input.merchant.trim(),
            note = input.note.trim(),
            createdAt = now,
            updatedAt = now
        )

        // Set saving immediately to prevent
        // concurrent submissions.
        _uiState.value = _uiState.value.copy(
            isSaving = true,
            errorMessage = null,
            operationSuccessful = false
        )

        viewModelScope.launch {
            try {
                val recentExpenses =
                    repository.getExpensesCreatedSince(
                        now - 60_000L
                    )

                val duplicate =
                    ExpenseRules.isDuplicateWithin60Seconds(
                        newExpense = expense,
                        recentExpenses = recentExpenses,
                        nowMillis = now
                    )

                if (duplicate) {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            pendingDuplicate = expense
                        )
                } else {
                    persistNewExpense(expense)
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to check recent expenses. " +
                                "Please retry."
                )
            }
        }
    }

    // -----------------------------------------
    // CONFIRM DUPLICATE EXPENSE
    // -----------------------------------------

    fun confirmDuplicateExpense() {
        if (_uiState.value.isSaving ||
            _uiState.value.isDeleting
        ) return

        val expense =
            _uiState.value.pendingDuplicate ?: return

        _uiState.value = _uiState.value.copy(
            isSaving = true,
            errorMessage = null
        )

        viewModelScope.launch {
            persistNewExpense(expense)
        }
    }

    fun cancelDuplicateExpense() {
        if (_uiState.value.isSaving) return

        _uiState.value = _uiState.value.copy(
            pendingDuplicate = null
        )
    }

    // -----------------------------------------
    // SAVE NEW EXPENSE
    // -----------------------------------------

    private suspend fun persistNewExpense(
        expense: Expense
    ) {
        try {
            repository.addExpense(expense)

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                pendingDuplicate = null,
                operationSuccessful = true,
                errorMessage = null,
                lastUsedPaymentMethod =
                    expense.paymentMethod
            )

        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                errorMessage =
                    "Unable to save expense. Please retry."
            )
        }
    }

    // -----------------------------------------
    // EDIT EXPENSE
    // -----------------------------------------

    fun updateExpense(
        expenseId: String,
        input: ExpenseFormData
    ) {
        if (_uiState.value.isSaving ||
            _uiState.value.isDeleting ||
            _uiState.value.pendingDuplicate != null
        ) return

        val amountMinor =
            ExpenseRules.parseAmount(input.amount)

        if (amountMinor == null) {
            showError("Enter a valid expense amount.")
            return
        }

        val validationError =
            ExpenseRules.validateExpense(
                amountMinor = amountMinor,
                categoryId = input.categoryId,
                date = input.date
            )

        if (validationError != null) {
            showError(validationError)
            return
        }

        _uiState.value = _uiState.value.copy(
            isSaving = true,
            errorMessage = null,
            operationSuccessful = false
        )

        viewModelScope.launch {
            try {
                val existing =
                    repository.getExpenseById(expenseId)

                if (existing == null) {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            errorMessage =
                                "Expense record not found."
                        )
                    return@launch
                }

                val classification =
                    ExpenseRules.resolveClassification(
                        categoryDefault =
                            input.categoryDefault,
                        userOverride =
                            input.classificationOverride
                    )

                val updated = existing.copy(
                    amountMinor = amountMinor,
                    categoryId = input.categoryId,
                    categoryName = input.categoryName,
                    classification = classification,
                    paymentMethod = input.paymentMethod,
                    date = input.date,
                    merchant = input.merchant.trim(),
                    note = input.note.trim(),
                    updatedAt = System.currentTimeMillis()
                )

                repository.updateExpense(updated)

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    operationSuccessful = true,
                    lastUsedPaymentMethod =
                        updated.paymentMethod
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage =
                        "Unable to update expense. " +
                                "Please retry."
                )
            }
        }
    }

    // -----------------------------------------
    // DELETE EXPENSE
    // -----------------------------------------

    // Call only after the UI has shown and
    // received deletion confirmation.
    fun deleteExpense(expenseId: String) {
        if (_uiState.value.isDeleting ||
            _uiState.value.isSaving
        ) return

        _uiState.value = _uiState.value.copy(
            isDeleting = true,
            errorMessage = null,
            operationSuccessful = false
        )

        viewModelScope.launch {
            try {
                repository.deleteExpense(expenseId)

                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    operationSuccessful = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    errorMessage =
                        "Unable to delete expense. " +
                                "Please retry."
                )
            }
        }
    }

    // -----------------------------------------
    // UI STATE HELPERS
    // -----------------------------------------

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
}
