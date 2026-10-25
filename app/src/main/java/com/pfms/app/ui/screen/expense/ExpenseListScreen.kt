package com.pfms.app.ui.screen.expense

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pfms.app.domain.model.Expense
import com.pfms.app.viewmodel.ExpenseListViewModel

@Composable
fun ExpenseListScreen(expenses: List<Expense>, isLoading: Boolean, isDeleting: Boolean,
    onAddExpense: () -> Unit, onQuickAdd: () -> Unit, onEditExpense: (String) -> Unit,
    onDeleteExpense: (String) -> Unit, onBack: () -> Unit, errorMessage: String? = null,
    onRepeatExpense: (String) -> Unit = {}, onRetry: (() -> Unit)? = null,
    onCategories: (() -> Unit)? = null, onRecurring: (() -> Unit)? = null,
    onSubscriptions: (() -> Unit)? = null, onHome: (() -> Unit)? = null, onIncome: (() -> Unit)? = null,
    onGoals: (() -> Unit)? = null, onInsights: (() -> Unit)? = null, onSpendingAnalytics: (() -> Unit)? = null) {
    val vm: ExpenseListViewModel = viewModel(factory = viewModelFactory { initializer { ExpenseListViewModel(createSavedStateHandle()) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(expenses, isLoading, isDeleting, errorMessage) { vm.sync(expenses, isLoading, isDeleting, errorMessage) }
    ExpenseOverviewScreen(state, ExpenseListActions(onBack, onAddExpense, onQuickAdd, onEditExpense, onRepeatExpense,
        vm::query, vm::category, vm::classification, vm::month, vm::moveMonth, vm::menu, vm::recordMenu, vm::delete,
        { vm.confirmDelete(onDeleteExpense) }, vm::sort, vm::clearFilters, onRetry, onCategories, onRecurring,
        onSubscriptions, onHome, onIncome, onGoals, onInsights, onSpendingAnalytics))
}
