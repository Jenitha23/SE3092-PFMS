package com.pfms.app.ui.screen.expense

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.ExpenseOperation
import com.pfms.app.viewmodel.ExpenseViewModel

@Composable
fun ExpenseFormContent(
    viewModel: ExpenseViewModel,
    expenseId: String? = null,
    categories: List<ExpenseCategoryOption>,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editing = expenseId != null
    val existing = uiState.expenses.firstOrNull { it.id == expenseId }

    // Prevent a success/error from a previous destination affecting this form.
    LaunchedEffect(expenseId) {
        viewModel.clearSuccess()
        viewModel.clearError()
    }

    val expected = if (editing) ExpenseOperation.UPDATE else ExpenseOperation.ADD
    LaunchedEffect(uiState.successfulOperation) {
        if (uiState.successfulOperation == expected) {
            viewModel.clearSuccess()
            onSaveSuccess()
        }
    }

    when {
        editing && uiState.isLoading -> CircularProgressIndicator()
        editing && existing == null -> Text("Expense record not found.")
        else -> ExpenseFormScreen(
            initialExpense = existing,
            categories = categories,
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            defaultPaymentMethod = uiState.lastUsedPaymentMethod,
            onBack = onBack,
            onSave = { form ->
                if (expenseId != null) {
                    viewModel.updateExpense(expenseId, form)
                } else {
                    viewModel.addExpense(form)
                }
            }
        )
    }

    if (uiState.pendingDuplicate != null) {
        ExpenseDuplicateDialog(
            isSaving = uiState.isSaving,
            onConfirm = viewModel::confirmDuplicateExpense,
            onDismiss = viewModel::cancelDuplicateExpense
        )
    }
}
