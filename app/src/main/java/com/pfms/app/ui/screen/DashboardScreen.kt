package com.pfms.app.ui.screen

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.MonthlyDataIndicator
import com.pfms.app.ui.component.WelcomeBackIndicator
import com.pfms.app.viewmodel.*

/** Route adapter: state ownership, authentication and repository work remain in ViewModels. */
@Composable
fun DashboardScreen(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    savingsGoalViewModel: SavingsGoalViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToIncome: () -> Unit = {},
    onNavigateToAddIncome: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToExpenses: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToAddExpense: () -> Unit = {},
    onQuickAddExpense: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onTransactionClick: (Transaction) -> Unit = {},
    dashboardViewModel: DashboardViewModel = hiltViewModel()
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val logout by sessionViewModel.logoutResult.collectAsStateWithLifecycle()
    val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val chrome by dashboardViewModel.chrome.collectAsStateWithLifecycle()
    val goal by savingsGoalViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(profile, session) { dashboardViewModel.onIdentityChanged(profile, session) }
    LaunchedEffect(goal) { dashboardViewModel.onGoalChanged(goal) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { dashboardViewModel.retry() }
    DashboardStatusBar()
    DashboardScreenContent(state, chrome, dashboardViewModel.monthChoices,
        actions = DashboardActions(
            onSettings = onNavigateToSettings, onIncome = onNavigateToIncome,
            onAddIncome = onNavigateToAddIncome, onExpenses = onNavigateToExpenses,
            onAddExpense = onNavigateToAddExpense, onQuickAdd = onQuickAddExpense,
            onGoals = onNavigateToGoals, onInsights = onNavigateToAnalytics,
            onHistory = onNavigateToHistory, onTransaction = onTransactionClick,
            onPreviousMonth = dashboardViewModel::previousMonth,
            onNextMonth = dashboardViewModel::nextMonth,
            onShowMonths = dashboardViewModel::showMonthMenu,
            onDismissMonths = dashboardViewModel::dismissMonthMenu,
            onSelectMonth = dashboardViewModel::selectMonth,
            onRetry = dashboardViewModel::retry
        ), notices = {
            WelcomeBackIndicator(onNavigateToAddIncome, onNavigateToAddExpense)
            if (!state.isLoading && state.errorMessage == null) MonthlyDataIndicator(state.selectedMonth)
            (logout as? LogoutResult.Failure)?.let { ErrorBanner(it.message) }
            Spacer(Modifier.height(8.dp))
        })
    if (logout is LogoutResult.UnsynchronizedDataWarning) {
        AlertDialog(onDismissRequest = sessionViewModel::consumeLogoutResult,
            title = { Text("Unsynchronized Data") },
            text = { Text("You have offline financial entries that have not synced with the cloud yet. " +
                "Logging out will discard unsaved local changes. Do you still want to log out?") },
            confirmButton = {
                TextButton(onClick = { sessionViewModel.logout(force = true) }) { Text("Log Out Anyway") }
            },
            dismissButton = {
                TextButton(onClick = sessionViewModel::consumeLogoutResult) { Text("Cancel") }
            })
    }
}

@Composable
private fun DashboardStatusBar() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}
