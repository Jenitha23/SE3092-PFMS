package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.ExpenseCategory
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.RecurringExpense
import com.pfms.app.domain.model.SubscriptionFlag
import com.pfms.app.domain.repository.AdvancedExpenseRepository
import com.pfms.app.domain.usecase.RecurringExpenseCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedExpenseViewModel @Inject constructor(
    private val repository: AdvancedExpenseRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val reload = MutableStateFlow(0)

    private fun <T> observe(label: String, load: () -> Flow<List<T>>): StateFlow<List<T>> =
        reload.flatMapLatest {
            flow { emitAll(load()) }.retryWhen { cause, attempt ->
                if (cause is CancellationException) throw cause
                _message.value = "Unable to load $label. Retrying; you can also tap Retry."
                delay((attempt + 1).coerceAtMost(15) * 2_000L)
                true
            }.onEach {
                if (_message.value == "Unable to load $label. Retrying; you can also tap Retry.") _message.value = null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), emptyList())

    val categories = observe("categories", repository::categories)
    val templates = observe("recurring expenses", repository::templates)

    fun retryLoading() {
        _message.value = null
        reload.value++
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val monthlySubscriptionMinor: StateFlow<Long?> = templates.map { items ->
        try {
            RecurringExpenseCalculator.calculateMonthlySubscriptionTotal(items)
        } catch (e: ArithmeticException) {
            _message.value = "The monthly subscription total exceeds the supported amount. Edit the subscription amounts."
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun ensureDefaults() = runTask {
        repository.initializeDefaults()
    }

    private fun runTask(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try {
                withTimeout(20_000) { block() }
                onSuccess()
            } catch (e: TimeoutCancellationException) {
                _message.value = "The connection timed out. Check the list before retrying; pending changes may still sync."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = e.message ?: "Operation failed. Please retry."
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun createCategory(name: String, classification: ExpenseClassification, onSuccess: () -> Unit = {}) = runTask(onSuccess) {
        repository.createCategory(name, classification)
    }

    fun updateCategory(category: ExpenseCategory, onSuccess: () -> Unit = {}) = runTask(onSuccess) {
        repository.updateCategory(category)
    }

    fun saveTemplate(template: RecurringExpense, onSuccess: () -> Unit = {}) = runTask(onSuccess) {
        repository.saveTemplate(template)
    }

    fun deleteTemplate(templateId: String) = runTask {
        repository.deleteTemplate(templateId)
    }

    fun confirm(template: RecurringExpense) = runTask {
        repository.confirmDue(template)
    }

    fun repeatExpense(expenseId: String) = runTask {
        repository.repeatExpense(expenseId)
    }

    /**
     * Marks a subscription as cancelled according to FR-20:
     * - Pauses active recurrence so no more due confirmations occur.
     * - Flags as CANCEL.
     * - Excludes from monthly subscription totals.
     * - Clears future reminder date.
     * - Preserves past transaction history.
     */
    fun cancelSubscription(template: RecurringExpense) = runTask {
        val updated = template.copy(
            active = false,
            flag = SubscriptionFlag.CANCEL,
            reminderDate = ""
        )
        repository.saveTemplate(updated)
    }

    fun toggleActive(template: RecurringExpense) = runTask {
        repository.saveTemplate(template.copy(
            active = !template.active,
            flag = if (!template.active && template.flag == SubscriptionFlag.CANCEL) SubscriptionFlag.NONE else template.flag
        ))
    }

    fun isDue(template: RecurringExpense): Boolean =
        RecurringExpenseCalculator.isDue(template)
}
