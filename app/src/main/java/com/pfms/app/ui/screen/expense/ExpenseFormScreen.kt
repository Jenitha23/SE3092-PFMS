package com.pfms.app.ui.screen.expense

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pfms.app.domain.model.*
import com.pfms.app.viewmodel.*

@Composable
fun ExpenseFormScreen(initialExpense: Expense?, categories: List<ExpenseCategoryOption>, isSaving: Boolean,
    onSave: (ExpenseFormData) -> Unit, onBack: () -> Unit, errorMessage: String? = null,
    defaultPaymentMethod: ExpensePaymentMethod = ExpensePaymentMethod.CASH,
    isRepeating: Boolean = false, pastExpenses: List<Expense> = emptyList(),
    onManageRecurring: (() -> Unit)? = null, canRetry: Boolean = false) {
    val vm: ExpenseDraftViewModel = viewModel(key = "expense-form-${initialExpense?.id ?: "new"}-$isRepeating",
        factory = viewModelFactory { initializer { ExpenseDraftViewModel(createSavedStateHandle(), initialExpense, defaultPaymentMethod, isRepeating) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(categories, pastExpenses, isSaving, errorMessage, canRetry) { vm.sync(categories, pastExpenses, isSaving, errorMessage, canRetry) }
    ExpenseEditorScreen(state, expenseDraftActions(vm, onSave, onBack), onManageRecurring)
}

internal fun expenseDraftActions(vm: ExpenseDraftViewModel, onSave: (ExpenseFormData) -> Unit, onBack: () -> Unit) = ExpenseDraftActions(
    onBack, { vm.submit(onSave) }, vm::amount, vm::category, vm::payment, vm::classification,
    vm::merchant, vm::note, vm::menu, vm::increment, vm::date, vm::repeat)
