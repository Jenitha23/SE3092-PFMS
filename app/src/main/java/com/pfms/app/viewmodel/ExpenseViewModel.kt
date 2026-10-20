package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.DuplicateExpenseException
import com.pfms.app.domain.repository.ExpenseRepository
import com.pfms.app.domain.usecase.ExpenseRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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

enum class ExpenseOperation { ADD, UPDATE, DELETE }

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val pendingDuplicate: Expense? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val operationSuccessful: Boolean = false,
    val successfulOperation: ExpenseOperation? = null,
    val lastUsedPaymentMethod: ExpensePaymentMethod =
        ExpensePaymentMethod.CASH
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(ExpenseUiState())

    val uiState: StateFlow<ExpenseUiState> =
        _uiState.asStateFlow()

    private val colomboZone =
        ZoneId.of("Asia/Colombo")

    private var observation: Job? = null
    private var storedConflict: Expense? = null
    private var createId: String? = null
    private var failedAction: (() -> Unit)? = null
    private var loadFailed = false

    fun beginForm() {
        createId = null
        clearError()
        clearSuccess()
    }

    fun retry() {
        if (_uiState.value.isSaving || _uiState.value.isDeleting || !_uiState.value.canRetry) return
        failedAction?.invoke()
    }

    private fun failure(error: Throwable, action: ErrorAction, retry: () -> Unit) {
        val problem = OperationErrors.present(error, action)
        if (action != ErrorAction.LOAD) loadFailed = false
        failedAction = retry.takeIf { problem.canRetry }
        _uiState.value = _uiState.value.copy(isSaving = if (action == ErrorAction.LOAD) _uiState.value.isSaving else false,
            isDeleting = if (action == ErrorAction.LOAD) _uiState.value.isDeleting else false,
            errorMessage = problem.message, canRetry = problem.canRetry)
    }

    init {
        observeExpenses()
        viewModelScope.launch {
            repository.observeLastPaymentMethod().catch { error -> failure(error, ErrorAction.LOAD, ::retryLoading) }.collect { method ->
                _uiState.value = _uiState.value.copy(lastUsedPaymentMethod = method)
            }
        }
        viewModelScope.launch {
            repository.observeDuplicateConflict().catch { error -> failure(error, ErrorAction.LOAD, ::retryLoading) }.collect { expense ->
                val old = storedConflict
                storedConflict = expense
                val pending = _uiState.value.pendingDuplicate
                if (pending == null || pending.id == old?.id) {
                    _uiState.value = _uiState.value.copy(pendingDuplicate = expense)
                }
            }
        }
    }

    // -----------------------------------------
    // OBSERVE EXPENSE RECORDS
    // -----------------------------------------

    private fun observeExpenses() {
        observation?.cancel()
        observation = viewModelScope.launch {
            flow { emitAll(repository.observeExpenses()) }
                .retryWhen { cause, attempt ->
                    if (cause is CancellationException) throw cause
                    loadFailed = true
                    failure(cause, ErrorAction.LOAD, ::retryLoading)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    if (!_uiState.value.canRetry) return@retryWhen false
                    delay((attempt + 1).coerceAtMost(15) * 2_000L)
                    true
                }
                .catch { error ->
                    failure(error, ErrorAction.LOAD, ::retryLoading)
                    _uiState.value = _uiState.value.copy(isLoading = false)
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
                        errorMessage = if (loadFailed) null else _uiState.value.errorMessage,
                        canRetry = if (loadFailed) false else _uiState.value.canRetry
                    )
                    loadFailed = false
                }
        }
    }

    fun retryLoading() = observeExpenses()

    private suspend fun <T> networkOperation(block: suspend () -> T): T = withTimeout(20_000) { block() }

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
            id = createId ?: UUID.randomUUID().toString().also { createId = it },

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
            canRetry = false,
            operationSuccessful = false,
            successfulOperation = null
        )

        viewModelScope.launch {
            try {
                val recentExpenses =
                    networkOperation { repository.getExpensesCreatedSince(
                        now - 60_000L
                    ) }

                val duplicate =
                    ExpenseRules.isDuplicateWithin60Seconds(
                        newExpense = expense,
                        recentExpenses = recentExpenses.filter { it.id != expense.id },
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

            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                failure(e, ErrorAction.SAVE) { addExpense(input) }
            } catch (e: Exception) {
                failure(e, ErrorAction.SAVE) { addExpense(input) }
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
            persistNewExpense(expense, allowDuplicate = true)
        }
    }

    fun cancelDuplicateExpense() {
        if (_uiState.value.isSaving) return
        val expense = _uiState.value.pendingDuplicate ?: return
        viewModelScope.launch {
            try {
                repository.dismissDuplicate(expense.id)
                _uiState.value = _uiState.value.copy(pendingDuplicate = storedConflict?.takeIf { it.id != expense.id })
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { showError("Unable to dismiss duplicate. Please retry.") }
        }
    }

    // -----------------------------------------
    // SAVE NEW EXPENSE
    // -----------------------------------------

    private suspend fun persistNewExpense(
        expense: Expense,
        allowDuplicate: Boolean = false
    ) {
        try {
            networkOperation { repository.addExpenseChecked(expense, allowDuplicate) }
            createId = null

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                pendingDuplicate = storedConflict?.takeIf { it.id != expense.id },
                operationSuccessful = true,
                successfulOperation = ExpenseOperation.ADD,
                errorMessage = null,
                canRetry = false,
                lastUsedPaymentMethod =
                    expense.paymentMethod
            )

        } catch (e: DuplicateExpenseException) {
            _uiState.value = _uiState.value.copy(isSaving = false, pendingDuplicate = expense)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            failure(e, ErrorAction.SAVE) { retryNewExpense(expense, allowDuplicate) }
        } catch (e: Exception) {
            failure(e, ErrorAction.SAVE) { retryNewExpense(expense, allowDuplicate) }
        }
    }

    private fun retryNewExpense(expense: Expense, allowDuplicate: Boolean) {
        if (_uiState.value.isSaving || _uiState.value.isDeleting) return
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null, canRetry = false)
        viewModelScope.launch { persistNewExpense(expense, allowDuplicate) }
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
            canRetry = false,
            operationSuccessful = false,
            successfulOperation = null
        )

        viewModelScope.launch {
            try {
                val existing =
                    networkOperation { repository.getExpenseById(expenseId) }

                if (existing == null) {
                    _uiState.value =
                        _uiState.value.copy(
                            isSaving = false,
                            errorMessage =
                                "This expense is no longer available. Return to the list and choose another record.",
                            canRetry = false
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

                networkOperation { repository.updateExpense(updated) }

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    operationSuccessful = true,
                    successfulOperation = ExpenseOperation.UPDATE,
                    lastUsedPaymentMethod =
                        updated.paymentMethod
                )

            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                failure(e, ErrorAction.SAVE) { updateExpense(expenseId, input) }
            } catch (e: Exception) {
                failure(e, ErrorAction.SAVE) { updateExpense(expenseId, input) }
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
            canRetry = false,
            operationSuccessful = false,
            successfulOperation = null
        )

        viewModelScope.launch {
            try {
                networkOperation { repository.deleteExpense(expenseId) }

                _uiState.value = _uiState.value.copy(
                    isDeleting = false,
                    operationSuccessful = true,
                    successfulOperation = ExpenseOperation.DELETE
                )
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                failure(e, ErrorAction.DELETE) { deleteExpense(expenseId) }
            } catch (e: Exception) {
                failure(e, ErrorAction.DELETE) { deleteExpense(expenseId) }
            }
        }
    }

    // -----------------------------------------
    // UI STATE HELPERS
    // -----------------------------------------

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(
            operationSuccessful = false,
            successfulOperation = null
        )
    }

    fun clearError() {
        failedAction = null
        _uiState.value = _uiState.value.copy(
            errorMessage = null, canRetry = false
        )
    }

    private fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message, canRetry = false
        )
    }
}
