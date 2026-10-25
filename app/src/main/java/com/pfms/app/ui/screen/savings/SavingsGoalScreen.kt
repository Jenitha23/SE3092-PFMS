package com.pfms.app.ui.screen.savings

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.domain.model.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.*

@Composable
fun SavingsGoalScreen(viewModel: SavingsGoalViewModel = hiltViewModel(), onBack: () -> Unit = {},
    onOpenSavingsSimulator: () -> Unit = {}, modifier: Modifier = Modifier,
    onHome: (() -> Unit)? = null, onIncome: (() -> Unit)? = null, onExpenses: (() -> Unit)? = null, onInsights: (() -> Unit)? = null) {
    val source by viewModel.uiState.collectAsStateWithLifecycle()
    SavingsGoalContent(source, onBack, viewModel::createGoal, viewModel::updateGoal, viewModel::archiveGoal,
        viewModel::deleteGoal, { amount, note, date -> viewModel.addContribution(amount, note, date) },
        { amount, note, date -> viewModel.addWithdrawal(amount, note, date) }, viewModel::updateTransaction, viewModel::deleteTransaction,
        viewModel::clearError, modifier, viewModel::retry, viewModel::beginForm, onOpenSavingsSimulator,
        onHome, onIncome, onExpenses, onInsights)
}

/** Callback adapter retained for previews and integration tests. The controller owns all draft state. */
@Composable
fun SavingsGoalContent(uiState: SavingsGoalUiState, onBack: () -> Unit,
    onCreateGoal: (String, Long, String, Long) -> Unit, onUpdateGoal: (SavingsGoal) -> Unit,
    onArchiveGoal: (String) -> Unit, onDeleteGoal: (String) -> Unit,
    onAddContribution: (Long, String?, String?) -> Unit, onAddWithdrawal: (Long, String?, String?) -> Unit,
    onUpdateTransaction: (GoalTransaction) -> Unit, onDeleteTransaction: (String) -> Unit, onClearError: () -> Unit,
    modifier: Modifier = Modifier, onRetry: () -> Unit = {}, onBeginForm: () -> Unit = {}, onOpenSavingsSimulator: () -> Unit = {},
    onHome: (() -> Unit)? = null, onIncome: (() -> Unit)? = null, onExpenses: (() -> Unit)? = null, onInsights: (() -> Unit)? = null
) = FinanceTheme {
    val controller: SavingsGoalScreenViewModel = viewModel()
    val state by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(uiState) { controller.sync(uiState) }
    val actions = SavingsScreenActions(
        create = { onBeginForm(); controller.openGoal() },
        idea = { key -> onBeginForm(); controller.openGoal(idea = key) },
        edit = { onBeginForm(); controller.openGoal(editing = true) },
        contribute = { onBeginForm(); controller.openTransaction(GoalTransactionType.CONTRIBUTION) },
        withdraw = { onBeginForm(); controller.openTransaction(GoalTransactionType.WITHDRAWAL) },
        editTransaction = { tx -> onBeginForm(); controller.openTransaction(tx.transactionType, tx) },
        deleteTransaction = { id -> controller.confirmation("transaction", id) },
        archive = { controller.confirmation("archive") }, delete = { controller.confirmation("delete") },
        help = { controller.help(true) }, browse = controller::browseIdeas, retry = onRetry, clear = onClearError)
    SavingsGoalRedesignScreen(state, actions, onBack, onOpenSavingsSimulator,
        modifier.blur(if (state.goalDraft != null) 3.dp else 0.dp), onHome, onIncome, onExpenses, onInsights)
    if (state.goalDraft != null) SavingsGoalEditorDialog(state, controller,
        onSave = { controller.submitGoal(onCreateGoal, onUpdateGoal) })
    if (state.transactionDraft != null) SavingsTransactionEditorDialog(state, controller,
        onSave = { controller.submitTransaction(onAddContribution, onAddWithdrawal, onUpdateTransaction) })
    SavingsGoalOverlays(state, controller, onArchiveGoal, onDeleteGoal, onDeleteTransaction)
}

data class SavingsScreenActions(val create: () -> Unit, val idea: (String) -> Unit, val edit: () -> Unit,
    val contribute: () -> Unit, val withdraw: () -> Unit, val editTransaction: (GoalTransaction) -> Unit,
    val deleteTransaction: (String) -> Unit, val archive: () -> Unit, val delete: () -> Unit,
    val help: () -> Unit, val browse: () -> Unit, val retry: () -> Unit, val clear: () -> Unit)
