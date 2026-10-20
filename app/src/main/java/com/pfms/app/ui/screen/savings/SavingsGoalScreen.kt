package com.pfms.app.ui.screen.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.GoalStatus
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.ui.theme.DarkNavyText
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.SavingsGoalUiState
import com.pfms.app.viewmodel.SavingsGoalViewModel

/**
 * State-holding Route Composable for the Savings Goal screen.
 */
@Composable
fun SavingsGoalScreen(
    viewModel: SavingsGoalViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SavingsGoalContent(
        uiState = uiState,
        onBack = onBack,
        onCreateGoal = { name, targetAmountMinor, targetDate, openingSavedMinor ->
            val goal = SavingsGoal(
                name = name,
                targetAmountMinor = targetAmountMinor,
                targetDate = targetDate,
                openingSavedAmountMinor = openingSavedMinor,
                status = GoalStatus.ACTIVE
            )
            viewModel.createGoal(goal)
        },
        onUpdateGoal = { goal ->
            viewModel.updateGoal(goal)
        },
        onArchiveGoal = { goalId ->
            viewModel.archiveGoal(goalId)
        },
        onDeleteGoal = { goalId ->
            viewModel.deleteGoal(goalId)
        },
        onAddContribution = { amountMinor, note, date ->
            viewModel.addContribution(amountMinor = amountMinor, note = note, date = date)
        },
        onAddWithdrawal = { amountMinor, note, date ->
            viewModel.addWithdrawal(amountMinor = amountMinor, note = note, date = date)
        },
        onUpdateTransaction = { tx ->
            viewModel.updateTransaction(tx)
        },
        onDeleteTransaction = { txId ->
            viewModel.deleteTransaction(txId)
        },
        onClearError = {
            viewModel.clearError()
        },
        modifier = modifier,
        onRetry = viewModel::retry,
        onBeginForm = viewModel::beginForm
    )
}

/**
 * Pure Content Composable separated for testability and Compose Previews (Task 24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalContent(
    uiState: SavingsGoalUiState,
    onBack: () -> Unit,
    onCreateGoal: (name: String, targetAmountMinor: Long, targetDate: String, openingSavedMinor: Long) -> Unit,
    onUpdateGoal: (SavingsGoal) -> Unit,
    onArchiveGoal: (String) -> Unit,
    onDeleteGoal: (String) -> Unit,
    onAddContribution: (Long, String?, String?) -> Unit,
    onAddWithdrawal: (Long, String?, String?) -> Unit,
    onUpdateTransaction: (GoalTransaction) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onBeginForm: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateGoalDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var activeTransactionTypeForAdd by remember { mutableStateOf<GoalTransactionType?>(null) }
    var transactionToEdit by remember { mutableStateOf<GoalTransaction?>(null) }
    var transactionIdToDelete by remember { mutableStateOf<String?>(null) }
    var showArchiveGoalConfirm by remember { mutableStateOf(false) }
    var showDeleteGoalConfirm by remember { mutableStateOf(false) }

    val initialSuccess = remember { uiState.successVersion }
    LaunchedEffect(uiState.successVersion) {
        if (uiState.successVersion != initialSuccess) {
            showCreateGoalDialog = false
            showEditGoalDialog = false
            activeTransactionTypeForAdd = null
            transactionToEdit = null
        }
    }
    LaunchedEffect(showCreateGoalDialog, showEditGoalDialog, activeTransactionTypeForAdd, transactionToEdit?.id) {
        if (showCreateGoalDialog || showEditGoalDialog || activeTransactionTypeForAdd != null || transactionToEdit != null) onBeginForm()
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = if (uiState.canRetry) "Retry" else null,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onRetry()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF7F9FC),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Savings Goal",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = DarkNavyText
                        )
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "Back",
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            fontSize = 15.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF7F9FC)
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val goal = uiState.activeGoal

                if (goal == null) {
                    item {
                        SavingsGoalEmptyState(
                            onCreateGoalClick = { showCreateGoalDialog = true }
                        )
                    }
                } else {
                    if (uiState.isOverdue) {
                        item {
                            OverdueAlertCard(
                                onUpdateDateClick = { showEditGoalDialog = true }
                            )
                        }
                    }

                    if (uiState.isCompleted) {
                        item {
                            CompletedCelebrationCard(
                                onArchiveGoal = { showArchiveGoalConfirm = true }
                            )
                        }
                    }

                    item {
                        SavingsGoalSummaryCard(
                            goal = goal,
                            uiState = uiState,
                            onEditGoal = { showEditGoalDialog = true },
                            onArchiveGoal = { showArchiveGoalConfirm = true },
                            onDeleteGoal = { showDeleteGoalConfirm = true }
                        )
                    }

                    item {
                        SavingsGoalActionButtons(
                            onAddContributionClick = {
                                activeTransactionTypeForAdd = GoalTransactionType.CONTRIBUTION
                            },
                            onAddWithdrawalClick = {
                                activeTransactionTypeForAdd = GoalTransactionType.WITHDRAWAL
                            },
                            operationInProgress = uiState.operationInProgress
                        )
                    }

                    item {
                        SavingsPaceCard(uiState = uiState)
                    }

                    item {
                        DiscretionaryImpactCard(uiState = uiState)
                    }

                    item {
                        TransactionLedgerSection(
                            transactions = uiState.transactions,
                            onAddContribution = { activeTransactionTypeForAdd = GoalTransactionType.CONTRIBUTION },
                            onEditTransaction = { tx -> transactionToEdit = tx },
                            onDeleteTransaction = { txId -> transactionIdToDelete = txId }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // ── Dialog Overlays ─────────────────────────────────────────

    if (showCreateGoalDialog) {
        CreateGoalDialog(
            onDismiss = { showCreateGoalDialog = false },
            onConfirm = { name, targetMinor, targetDate, openingSavedMinor ->
                onCreateGoal(name, targetMinor, targetDate, openingSavedMinor)
            },
            operationInProgress = uiState.operationInProgress,
            errorMessage = uiState.errorMessage,
            canRetry = uiState.canRetry
        )
    }

    if (showEditGoalDialog && uiState.activeGoal != null) {
        EditGoalDialog(
            goal = uiState.activeGoal,
            onDismiss = { showEditGoalDialog = false },
            onConfirm = { updatedGoal ->
                onUpdateGoal(updatedGoal)
            },
            operationInProgress = uiState.operationInProgress,
            errorMessage = uiState.errorMessage,
            canRetry = uiState.canRetry
        )
    }

    activeTransactionTypeForAdd?.let { txType ->
        AddTransactionDialog(
            transactionType = txType,
            onDismiss = { activeTransactionTypeForAdd = null },
            onConfirm = { amountMinor, note, date ->
                if (txType == GoalTransactionType.CONTRIBUTION) {
                    onAddContribution(amountMinor, note, date)
                } else {
                    onAddWithdrawal(amountMinor, note, date)
                }
            },
            operationInProgress = uiState.operationInProgress,
            errorMessage = uiState.errorMessage,
            canRetry = uiState.canRetry
        )
    }

    transactionToEdit?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            onDismiss = { transactionToEdit = null },
            onConfirm = { updatedTx ->
                onUpdateTransaction(updatedTx)
            },
            operationInProgress = uiState.operationInProgress,
            errorMessage = uiState.errorMessage,
            canRetry = uiState.canRetry
        )
    }

    transactionIdToDelete?.let { txId ->
        ConfirmActionDialog(
            title = "Delete Transaction",
            message = "Are you sure you want to remove this transaction from the savings ledger? This action cannot be undone.",
            confirmButtonText = "Delete",
            isDestructive = true,
            onDismiss = { transactionIdToDelete = null },
            onConfirm = {
                onDeleteTransaction(txId)
                transactionIdToDelete = null
            },
            operationInProgress = uiState.operationInProgress
        )
    }

    if (showArchiveGoalConfirm && uiState.activeGoal != null) {
        ConfirmActionDialog(
            title = "Archive Savings Goal",
            message = "Are you sure you want to archive \"${uiState.activeGoal.name}\"? It will be moved to archived goals.",
            confirmButtonText = "Archive",
            isDestructive = false,
            onDismiss = { showArchiveGoalConfirm = false },
            onConfirm = {
                onArchiveGoal(uiState.activeGoal.id)
                showArchiveGoalConfirm = false
            },
            operationInProgress = uiState.operationInProgress
        )
    }

    if (showDeleteGoalConfirm && uiState.activeGoal != null) {
        ConfirmActionDialog(
            title = "Delete Savings Goal",
            message = "Are you sure you want to delete \"${uiState.activeGoal.name}\" and its ledger? This action cannot be undone.",
            confirmButtonText = "Delete Goal",
            isDestructive = true,
            onDismiss = { showDeleteGoalConfirm = false },
            onConfirm = {
                onDeleteGoal(uiState.activeGoal.id)
                showDeleteGoalConfirm = false
            },
            operationInProgress = uiState.operationInProgress
        )
    }
}
