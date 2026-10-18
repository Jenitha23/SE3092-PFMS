
package com.pfms.app.ui.screen.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.pfms.app.viewmodel.ExpenseViewModel

/**
 * Connects ExpenseViewModel state to ExpenseListScreen.
 *
 * This component does not directly access Room or Firebase.
 * The ViewModel must be supplied by the parent.
 */
@Composable
fun ExpenseListContent(
    viewModel: ExpenseViewModel,
    onAddExpense: () -> Unit,
    onQuickAdd: () -> Unit,
    onEditExpense: (String) -> Unit,
    onBack: () -> Unit,
    onRepeatExpense: (String) -> Unit = {},
    onCategories: (() -> Unit)? = null,
    onRecurring: (() -> Unit)? = null,
    onSubscriptions: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    onIncome: (() -> Unit)? = null,
    onGoals: (() -> Unit)? = null,
    onInsights: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState
        .collectAsStateWithLifecycle()

    ExpenseListScreen(
        expenses = uiState.expenses,
        isLoading = uiState.isLoading,
        isDeleting = uiState.isDeleting,
        errorMessage = uiState.errorMessage,

        onAddExpense = onAddExpense,

        onQuickAdd = onQuickAdd,

        onEditExpense = onEditExpense,

        onDeleteExpense = { expenseId ->
            // ExpenseListScreen already asks the
            // user to confirm before calling this.
            viewModel.deleteExpense(expenseId)
        },

        onBack = onBack,
        onRepeatExpense = onRepeatExpense,
        onRetry = viewModel::retryLoading,
        onCategories = onCategories,
        onRecurring = onRecurring,
        onSubscriptions = onSubscriptions,
        onHome = onHome,
        onIncome = onIncome,
        onGoals = onGoals,
        onInsights = onInsights
    )
}
