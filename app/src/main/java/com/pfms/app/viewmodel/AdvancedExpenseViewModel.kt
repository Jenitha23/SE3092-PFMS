package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import kotlinx.coroutines.flow.catch
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
import java.util.UUID

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AdvancedExpenseViewModel @Inject constructor(
    private val repository: AdvancedExpenseRepository
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _canRetry = MutableStateFlow(false)
    val canRetry = _canRetry.asStateFlow()
    private var categoryDraftId: String? = null
    private var failedAction: (() -> Unit)? = null
    fun retry() { if (!_busy.value && _canRetry.value) failedAction?.invoke() }
    private val reload = MutableStateFlow(0)

    private fun <T> observe(label: String, load: () -> Flow<List<T>>): StateFlow<List<T>> =
        reload.flatMapLatest {
            var loadMessage: String? = null
            flow { emitAll(load()) }.retryWhen { cause, attempt ->
                if (cause is CancellationException) throw cause
                val problem = OperationErrors.present(cause, ErrorAction.LOAD)
                _message.value = problem.message
                loadMessage = problem.message
                _canRetry.value = problem.canRetry
                failedAction = ::retryLoading
                if (!problem.canRetry) return@retryWhen false
                delay((attempt + 1).coerceAtMost(15) * 2_000L)
                true
            }.catch { cause ->
                val problem = OperationErrors.present(cause, ErrorAction.LOAD)
                _message.value = problem.message
                _canRetry.value = problem.canRetry
            }.onEach {
                if (loadMessage != null && _message.value == loadMessage) {
                    _message.value = null
                    _canRetry.value = false
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), emptyList())

    val categories = observe("categories", repository::categories)
    val templates = observe("recurring expenses", repository::templates)

    fun retryLoading() {
        _message.value = null
        _canRetry.value = false
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
        _canRetry.value = false
        viewModelScope.launch {
            try {
                withTimeout(20_000) { block() }
                failedAction = null
                onSuccess()
            } catch (e: TimeoutCancellationException) {
                val problem = OperationErrors.present(e)
                _message.value = problem.message
                _canRetry.value = problem.canRetry
                failedAction = { runTask(onSuccess, block) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = OperationErrors.present(e)
                _message.value = problem.message
                _canRetry.value = problem.canRetry
                failedAction = { runTask(onSuccess, block) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
        _canRetry.value = false
        failedAction = null
    }

    fun createCategory(name: String, classification: ExpenseClassification, onSuccess: () -> Unit = {}) {
        val id = categoryDraftId ?: UUID.randomUUID().toString().also { categoryDraftId = it }
        runTask({ categoryDraftId = null; onSuccess() }) { repository.createCategoryWithId(id, name, classification) }
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

    fun confirm(template: RecurringExpense, onSuccess: () -> Unit = {}) = runTask(onSuccess) {
        repository.confirmDue(template)
    }

    fun repeatExpense(expenseId: String) {
        val id = UUID.randomUUID().toString()
        runTask { repository.repeatExpenseWithId(expenseId, id) }
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

    fun dismissReview(template: RecurringExpense) = runTask {
        val updated = template.copy(
            flag = if (template.flag == SubscriptionFlag.REVIEW) SubscriptionFlag.NONE else template.flag,
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

    fun isReviewDue(template: RecurringExpense): Boolean =
        RecurringExpenseCalculator.isReviewDue(template)
}
