package com.pfms.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import java.time.YearMonth

data class DashboardActions(
    val onSettings: () -> Unit = {}, val onIncome: () -> Unit = {},
    val onAddIncome: () -> Unit = {}, val onExpenses: () -> Unit = {},
    val onAddExpense: () -> Unit = {}, val onQuickAdd: () -> Unit = {},
    val onGoals: () -> Unit = {}, val onInsights: () -> Unit = {},
    val onHistory: () -> Unit = {}, val onTransaction: (Transaction) -> Unit = {},
    val onPreviousMonth: () -> Unit = {}, val onNextMonth: () -> Unit = {},
    val onShowMonths: () -> Unit = {}, val onDismissMonths: () -> Unit = {},
    val onSelectMonth: (YearMonth) -> Unit = {}, val onRetry: () -> Unit = {}
)

/** All cash amounts, ratios, labels and chart values arrive ready to display. */
@Composable
fun DashboardScreenContent(
    state: DashboardUiState,
    chrome: DashboardChromeState,
    monthChoices: List<Pair<YearMonth, String>> = emptyList(),
    actions: DashboardActions = DashboardActions(),
    notices: @Composable () -> Unit = {}
) {
    FinanceTheme {
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                PfmsBottomNavBar(PfmsNavTab.HOME, {}, actions.onIncome, actions.onExpenses,
                    actions.onGoals, actions.onInsights)
            }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("dashboard_sections"),
                contentPadding = PaddingValues(bottom = 20.dp)) {
                item("identity") { DashboardIdentityHeader(chrome.identity, actions.onSettings) }
                item("cashflow") {
                    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(
                        listOf(VaultDeep, VaultGreen, MaterialTheme.colorScheme.background)))
                        .padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DashboardCycleStrip(state.content)
                        DashboardCashflowCard(state, chrome, monthChoices, actions)
                        DashboardQuickActions(actions)
                        Spacer(Modifier.height(2.dp))
                    }
                }
                item("notices") { Column(Modifier.padding(horizontal = 20.dp)) { notices() } }
                if (state.errorMessage != null) {
                    item("error") {
                        Column(Modifier.padding(20.dp)) {
                            ErrorBanner(state.errorMessage)
                            if (state.canRetry) TextButton(onClick = actions.onRetry) { Text("Try again") }
                        }
                    }
                }
                if (state.isLoading) {
                    item("loading") {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading dashboard" })
                        }
                    }
                }
                state.content?.let { content ->
                    item("rhythm") {
                        DashboardRhythmCard(content, actions.onSelectMonth,
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    if (!content.hasData) {
                        item("empty") {
                            FinancePanel(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                Text("Start your monthly overview", style = MaterialTheme.typography.titleMedium)
                                Text("Add income or an expense to see your recorded cash flow.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    TextButton(onClick = actions.onAddIncome) { Text("Add income") }
                                    TextButton(onClick = actions.onAddExpense) { Text("Add expense") }
                                }
                            }
                        }
                    }
                    item("goal") {
                        DashboardGoalCard(chrome.goal, actions.onGoals,
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                    item("ledger") {
                        DashboardLedgerCard(content, actions,
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardIdentityHeader(identity: DashboardIdentity, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(VaultDeep).statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(15.dp), color = VaultGreen,
            border = androidx.compose.foundation.BorderStroke(1.dp, VaultMint.copy(alpha = 0.6f)),
            modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { FinanceIcon(FinanceSymbol.WALLET, tint = VaultMint) }
        }
        Column(Modifier.weight(1f)) {
            Text("TrackMyFinances", style = MaterialTheme.typography.titleMedium, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${identity.name} · ${identity.subtitle}", style = MaterialTheme.typography.bodySmall,
                color = VaultMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)
            .semantics { contentDescription = "Open reminder settings" }) {
            FinanceIcon(FinanceSymbol.BELL, tint = VaultMuted)
        }
        Surface(onClick = onSettings, shape = CircleShape, color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, VaultMint),
            modifier = Modifier.size(44.dp).semantics { contentDescription = "Open account settings" }) {
            Box(contentAlignment = Alignment.Center) {
                Text(identity.initials, color = VaultMint, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(name = "Dashboard loading", widthDp = 390, heightDp = 825)
@Composable
private fun DashboardLoadingPreview() {
    PFMSTheme(darkTheme = false) { DashboardScreenContent(DashboardUiState(), DashboardChromeState()) }
}
