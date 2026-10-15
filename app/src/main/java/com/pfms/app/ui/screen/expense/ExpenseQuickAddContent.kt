package com.pfms.app.ui.screen.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.ExpenseOperation
import com.pfms.app.viewmodel.ExpenseViewModel

@Composable
fun ExpenseQuickAddContent(
    viewModel: ExpenseViewModel,
    categories: List<ExpenseCategoryOption>,
    onDismiss: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.clearSuccess()
        viewModel.clearError()
    }

    LaunchedEffect(uiState.successfulOperation) {
        if (uiState.successfulOperation == ExpenseOperation.ADD) {
            viewModel.clearSuccess()
            onSaveSuccess()
        }
    }

    QuickAddExpenseSheet(
        categories = categories,
        defaultPaymentMethod = uiState.lastUsedPaymentMethod,
        isSaving = uiState.isSaving,
        errorMessage = uiState.errorMessage,
        onDismiss = onDismiss,
        onSave = { amount, categoryId, categoryName, default ->
            viewModel.quickAddExpense(amount, categoryId, categoryName, default)
        }
    )

    if (uiState.pendingDuplicate != null) {
        ExpenseDuplicateDialog(
            isSaving = uiState.isSaving,
            onConfirm = viewModel::confirmDuplicateExpense,
            onDismiss = viewModel::cancelDuplicateExpense
        )
    }
}
