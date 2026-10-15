
package com.pfms.app.ui.screen.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.ExpenseViewModel

/**
 * Connects the Quick Add bottom sheet to
 * the existing ExpenseViewModel.
 *
 * No direct Room or Firebase access.
 */
@Composable
fun ExpenseQuickAddContent(
    viewModel: ExpenseViewModel,
    categories: List<ExpenseCategoryOption>,
    onDismiss: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val uiState by viewModel.uiState
        .collectAsStateWithLifecycle()

    // Close only after a successful save.
    LaunchedEffect(uiState.operationSuccessful) {
        if (uiState.operationSuccessful) {
            viewModel.clearSuccess()
            onSaveSuccess()
        }
    }

    QuickAddExpenseSheet(
        categories = categories,
        defaultPaymentMethod =
            uiState.lastUsedPaymentMethod,
        isSaving = uiState.isSaving,
        errorMessage = uiState.errorMessage,
        onDismiss = onDismiss,
        onSave = { amount, categoryId,
                   categoryName, categoryDefault ->

            viewModel.quickAddExpense(
                amount = amount,
                categoryId = categoryId,
                categoryName = categoryName,
                categoryDefault = categoryDefault
            )
        }
    )

    // FR-22 duplicate confirmation.
    if (uiState.pendingDuplicate != null) {
        ExpenseDuplicateDialog(
            isSaving = uiState.isSaving,
            onConfirm = {
                viewModel.confirmDuplicateExpense()
            },
            onDismiss = {
                viewModel.cancelDuplicateExpense()
            }
        )
    }
}
