package com.pfms.app.viewmodel

import androidx.lifecycle.ViewModel
import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationErrors
import kotlinx.coroutines.flow.catch
import androidx.lifecycle.viewModelScope
import com.pfms.app.domain.model.ExpenseCategory
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.Recurrence
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import com.pfms.app.domain.usecase.ExpenseRules
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.math.BigDecimal

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

    private val controls = MutableStateFlow(AdvancedExpenseControls())
    val screen = combine(categories, templates, monthlySubscriptionMinor, controls, ::advancedExpenseScreenState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdvancedExpenseScreenState())

    fun showArchived(value: Boolean) { controls.update { it.copy(archived = value) } }
    fun categoryName(value: String) { controls.update { it.copy(categoryEditor = it.categoryEditor.copy(name = value.take(50), error = null)) } }
    fun categoryClassification(value: ExpenseClassification) { controls.update { it.copy(categoryEditor = it.categoryEditor.copy(classification = value)) } }
    fun resetCategoryEditor() { controls.update { it.copy(categoryEditor = CategoryEditorState()) } }
    fun editCategory(category: ExpenseCategory) {
        if (!category.custom || category.archived) return
        controls.update { it.copy(archived = false, categoryEditor = CategoryEditorState(category.name, category.classification, category)) }
    }
    fun saveCategoryDraft() {
        val draft = controls.value.categoryEditor
        val name = draft.name.trim()
        if (name.isBlank()) {
            controls.update { it.copy(categoryEditor = draft.copy(error = "Enter a category name.")) }; return
        }
        if (draft.original == null) createCategory(name, draft.classification, ::resetCategoryEditor)
        else updateCategory(draft.original.copy(name = name, classification = draft.classification), ::resetCategoryEditor)
    }
    fun requestArchive(category: ExpenseCategory?) { controls.update { it.copy(archiveTarget = category?.takeIf { c -> c.custom }) } }
    fun archiveSelectedCategory() {
        val category = controls.value.archiveTarget ?: return
        updateCategory(category.copy(archived = true)) {
            controls.update { it.copy(archiveTarget = null, categoryEditor = if (it.categoryEditor.original?.id == category.id) CategoryEditorState() else it.categoryEditor) }
        }
    }
    fun restoreCategory(category: ExpenseCategory) = updateCategory(category.copy(archived = false))

    fun openTemplate(subscription: Boolean, template: RecurringExpense? = null) {
        val original = template ?: RecurringExpense(id = UUID.randomUUID().toString(), subscription = subscription,
            categoryId = categories.value.firstOrNull { !it.archived }?.id.orEmpty(),
            nextDueDate = LocalDate.now(RecurringExpenseCalculator.COLOMBO_ZONE).toString())
        clearMessage()
        controls.update { it.copy(editor = RecurringEditorState(original, editing = template != null), menu = null) }
    }
    fun closeTemplate() { if (!_busy.value) controls.update { it.copy(editor = null) } }
    private fun changeTemplate(block: (RecurringEditorState) -> RecurringEditorState) {
        if (!_busy.value) controls.update { it.copy(editor = it.editor?.let(block)) }
    }
    fun templateTitle(value: String) = changeTemplate { it.copy(title = value, error = null) }
    fun templateAmount(value: String) = changeTemplate { it.copy(amount = value, error = null) }
    fun templateCategory(id: String) = changeTemplate { it.copy(categoryId = id, menu = null, error = null) }
    fun templatePayment(value: ExpensePaymentMethod) = changeTemplate { it.copy(payment = value, menu = null) }
    fun templateRecurrence(value: Recurrence) = changeTemplate { it.copy(recurrence = value) }
    fun templateFlag(value: SubscriptionFlag) = changeTemplate { it.copy(flag = value) }
    fun templateMenu(value: String?) = changeTemplate { it.copy(menu = value) }
    fun templateDate(millis: Long?, reminder: Boolean) {
        val date = millis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() } ?: return
        changeTemplate { if (reminder) it.copy(reminder = date, menu = null, error = null) else it.copy(due = date, menu = null, error = null) }
    }
    fun clearTemplateReminder() = changeTemplate { it.copy(reminder = "") }
    fun saveTemplateDraft() {
        val draft = controls.value.editor ?: return
        val amount = ExpenseRules.parseAmount(draft.amount)
        val category = categories.value.firstOrNull { it.id == draft.categoryId && !it.archived }
        val error = when {
            draft.title.isBlank() -> "Enter a template name."
            amount == null -> "Enter a positive amount with up to two decimal places."
            category == null -> "Select an active expense category."
            runCatching { LocalDate.parse(draft.due) }.isFailure -> "Choose a valid due date."
            draft.reminder.isNotBlank() && runCatching { LocalDate.parse(draft.reminder) }.isFailure -> "Choose a valid review date."
            else -> null
        }
        if (error != null) { changeTemplate { it.copy(error = error) }; return }
        saveTemplate(draft.original.copy(title = draft.title.trim(), amountMinor = requireNotNull(amount),
            categoryId = requireNotNull(category).id, categoryName = category.name, classification = category.classification,
            paymentMethod = draft.payment, recurrence = draft.recurrence, nextDueDate = draft.due,
            flag = draft.flag, reminderDate = draft.reminder)) { controls.update { it.copy(editor = null) } }
    }
    fun recordMenu(id: String?) { controls.update { it.copy(menu = id) } }
    fun recurringFilter(key: String) { controls.update { it.copy(recurringFilter = key) } }
    fun requestDelete(template: RecurringExpense?) { controls.update { it.copy(deleteTarget = template, menu = null) } }
    fun deleteSelectedTemplate() {
        val target = controls.value.deleteTarget ?: return
        runTask({ controls.update { it.copy(deleteTarget = null) } }) { repository.deleteTemplate(target.id) }
    }
    fun requestConfirmation(template: RecurringExpense?) {
        clearMessage()
        controls.update { it.copy(confirmTarget = template, confirmAmount = template?.let { t -> BigDecimal.valueOf(t.amountMinor, 2).toPlainString() }.orEmpty(), confirmError = null) }
    }
    fun confirmationAmount(value: String) { controls.update { it.copy(confirmAmount = value, confirmError = null) } }
    fun confirmSelectedTemplate() {
        val target = controls.value.confirmTarget ?: return
        val amount = ExpenseRules.parseAmount(controls.value.confirmAmount)
        if (amount == null) { controls.update { it.copy(confirmError = "Enter a positive amount with up to two decimal places.") }; return }
        confirm(target.copy(amountMinor = amount)) { controls.update { it.copy(confirmTarget = null) } }
    }

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
