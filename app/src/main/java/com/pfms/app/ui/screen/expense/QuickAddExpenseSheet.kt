package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pfms.app.domain.model.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddExpenseSheet(categories: List<ExpenseCategoryOption>, defaultPaymentMethod: ExpensePaymentMethod,
    isSaving: Boolean, onSave: (String, String, String, ExpenseClassification) -> Unit,
    onDismiss: () -> Unit, errorMessage: String? = null, canRetry: Boolean = false,
    onSaveDetails: ((ExpenseFormData) -> Unit)? = null, monthlyExpenses: List<Expense> = emptyList()) = FinanceTheme {
    // Each newly opened sheet gets its own draft; rotation retains its saved instance key.
    val instance = rememberSaveable { UUID.randomUUID().toString() }
    val vm: ExpenseDraftViewModel = viewModel(key = "expense-quick-$instance",
        factory = viewModelFactory { initializer { ExpenseDraftViewModel(createSavedStateHandle(), null, defaultPaymentMethod, quick = true) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(categories, monthlyExpenses, isSaving, errorMessage, canRetry) { vm.sync(categories, emptyList(), isSaving, errorMessage, canRetry, monthlyExpenses) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !state.saving || it != SheetValue.Hidden })
    ModalBottomSheet(onDismissRequest = { if (!state.saving) onDismiss() }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        ExpenseQuickEditor(state, expenseDraftActions(vm, { input ->
            onSaveDetails?.invoke(input) ?: onSave(input.amount, input.categoryId, input.categoryName, input.categoryDefault)
        }, onDismiss))
    }
}
