
package com.pfms.app.ui.screen.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.pfms.app.domain.model.Expense
import com.pfms.app.viewmodel.ExpenseViewModel

/**
 * Connects the Add/Edit Expense UI to ExpenseViewModel.
 *
 * This component does not access Room or Firebase.
 * The repository-backed ViewModel is supplied
 * by the parent navigation graph.
 */
@Composable
fun ExpenseFormContent(
    viewModel: ExpenseViewModel,
    expenseId: String? = null,
    categories: List<ExpenseCategoryOption>,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {

    val uiState by viewModel.uiState
        .collectAsStateWithLifecycle()

    val isEditing = expenseId != null

    // Find the selected expense from the
    // ViewModel's observed expense records.
    val existingExpense: Expense? =
        if (isEditing) {
            uiState.expenses.firstOrNull {
                it.id == expenseId
            }
        } else {
            null
        }

    // Navigate only after an actual successful save.
    LaunchedEffect(uiState.operationSuccessful) {
        if (uiState.operationSuccessful) {
            viewModel.clearSuccess()
            onSaveSuccess()
        }
    }

    when {

        // Wait for repository records to load.
        isEditing && uiState.isLoading -> {
            androidx.compose.material3.CircularProgressIndicator()
        }

        // Prevent editing an expense that does not exist.
        isEditing && existingExpense == null -> {

            androidx.compose.material3.Text(
                text = "Expense record not found."
            )
        }

        else -> {

            ExpenseFormScreen(
                initialExpense = existingExpense,
                categories = categories,

                isSaving = uiState.isSaving,

                errorMessage = uiState.errorMessage,

                defaultPaymentMethod =
                    uiState.lastUsedPaymentMethod,

                onBack = onBack,

                onSave = { formData ->

                    if (isEditing) {

                        viewModel.updateExpense(
                            expenseId = expenseId,
                            input = formData
                        )

                    } else {

                        viewModel.addExpense(
                            input = formData
                        )
                    }
                }
            )
        }
    }

    // FR-22: Show confirmation when an identical
    // expense exists within the previous 60 seconds.
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
