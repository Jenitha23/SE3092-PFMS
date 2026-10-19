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
    isRepeating: Boolean = false,
    categories: List<ExpenseCategoryOption>,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onManageRecurring: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editing = expenseId != null && !isRepeating
    val existing = uiState.expenses.firstOrNull { it.id == expenseId }
    val formCategories = if (editing && existing != null && categories.none { it.id == existing.categoryId }) {
        categories + ExpenseCategoryOption(existing.categoryId, existing.categoryName, existing.classification)
    } else categories

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
            categories = formCategories,
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            defaultPaymentMethod = uiState.lastUsedPaymentMethod,
            isRepeating = isRepeating,
            pastExpenses = uiState.expenses,
            onManageRecurring = onManageRecurring,
            onBack = onBack,
            onSave = { form ->
                if (editing && expenseId != null) {
                    viewModel.updateExpense(expenseId, form)
                } else {
                    viewModel.addExpense(form)
                }
            }
        )
    }

}
