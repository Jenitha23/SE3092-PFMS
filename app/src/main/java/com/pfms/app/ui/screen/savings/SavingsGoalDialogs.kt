package com.pfms.app.ui.screen.savings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.domain.model.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.*
import java.util.UUID

/** Compatibility adapters for callers opening an individual savings form. */
@Composable
private fun dialogController(source: SavingsGoalUiState, initialize: (SavingsGoalScreenViewModel) -> Unit): SavingsGoalScreenViewModel {
    val key = remember { "savings-dialog-${UUID.randomUUID()}" }
    val controller: SavingsGoalScreenViewModel = viewModel(key = key)
    LaunchedEffect(Unit) { controller.sync(source); initialize(controller) }
    LaunchedEffect(source) { controller.sync(source) }
    return controller
}

@Composable
fun CreateGoalDialog(onDismiss: () -> Unit, onConfirm: (String, Long, String, Long) -> Unit,
    operationInProgress: Boolean, errorMessage: String? = null, canRetry: Boolean = false) = FinanceTheme {
    val controller = dialogController(SavingsGoalUiState(isLoading = false, operationInProgress = operationInProgress,
        errorMessage = errorMessage, canRetry = canRetry)) { it.openGoal() }
    val state by controller.state.collectAsStateWithLifecycle()
    val initialized = remember { mutableStateOf(false) }
    LaunchedEffect(state.goalDraft) { if (state.goalDraft != null) initialized.value = true else if (initialized.value) onDismiss() }
    SavingsGoalEditorDialog(state, controller) { controller.submitGoal(onConfirm, {}) }
}

@Composable
fun EditGoalDialog(goal: SavingsGoal, onDismiss: () -> Unit, onConfirm: (SavingsGoal) -> Unit,
    operationInProgress: Boolean, errorMessage: String? = null, canRetry: Boolean = false) = FinanceTheme {
    val controller = dialogController(SavingsGoalUiState(isLoading = false, activeGoal = goal, operationInProgress = operationInProgress,
        errorMessage = errorMessage, canRetry = canRetry)) { it.openGoal(editing = true) }
    val state by controller.state.collectAsStateWithLifecycle()
    val initialized = remember { mutableStateOf(false) }
    LaunchedEffect(state.goalDraft) { if (state.goalDraft != null) initialized.value = true else if (initialized.value) onDismiss() }
    SavingsGoalEditorDialog(state, controller) { controller.submitGoal({ _, _, _, _ -> }, onConfirm) }
}

@Composable
fun AddTransactionDialog(transactionType: GoalTransactionType, availableGoalBalanceMinor: Long = 0,
    onDismiss: () -> Unit, onConfirm: (Long, String?, String) -> Unit, operationInProgress: Boolean,
    errorMessage: String? = null, canRetry: Boolean = false, onClearError: () -> Unit = {}) = FinanceTheme {
    val controller = dialogController(SavingsGoalUiState(isLoading = false, currentSavedMinor = availableGoalBalanceMinor,
        operationInProgress = operationInProgress, errorMessage = errorMessage, canRetry = canRetry)) { it.openTransaction(transactionType, balanceProvided = true) }
    val state by controller.state.collectAsStateWithLifecycle()
    val initialized = remember { mutableStateOf(false) }
    LaunchedEffect(state.transactionDraft) { if (state.transactionDraft != null) initialized.value = true else if (initialized.value) onDismiss() }
    SavingsTransactionEditorDialog(state, controller, onClearError) { controller.submitTransaction(
        { amount, note, date -> onConfirm(amount, note, date.orEmpty()) }, { amount, note, date -> onConfirm(amount, note, date.orEmpty()) }, {}) }
}

@Composable
fun EditTransactionDialog(transaction: GoalTransaction, onDismiss: () -> Unit, onConfirm: (GoalTransaction) -> Unit,
    operationInProgress: Boolean, errorMessage: String? = null, canRetry: Boolean = false,
    goal: SavingsGoal? = null, transactions: List<GoalTransaction> = emptyList(), onClearError: () -> Unit = {}) = FinanceTheme {
    val controller = dialogController(SavingsGoalUiState(isLoading = false, activeGoal = goal, transactions = transactions, operationInProgress = operationInProgress,
        errorMessage = errorMessage, canRetry = canRetry)) { it.openTransaction(transaction.transactionType, transaction) }
    val state by controller.state.collectAsStateWithLifecycle()
    val initialized = remember { mutableStateOf(false) }
    LaunchedEffect(state.transactionDraft) { if (state.transactionDraft != null) initialized.value = true else if (initialized.value) onDismiss() }
    SavingsTransactionEditorDialog(state, controller, onClearError) { controller.submitTransaction({ _, _, _ -> }, { _, _, _ -> }, onConfirm) }
}

@Composable
fun ConfirmActionDialog(title: String, message: String, confirmButtonText: String = "Confirm", isDestructive: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit, operationInProgress: Boolean) = FinanceTheme {
    AlertDialog(onDismissRequest = { if (!operationInProgress) onDismiss() }, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !operationInProgress) {
            Text(confirmButtonText, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !operationInProgress) { Text("Cancel") } })
}
