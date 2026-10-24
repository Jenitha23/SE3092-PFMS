package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.*
import java.time.YearMonth

data class IncomeStreamActions(
    val onBack: () -> Unit = {}, val onAdd: () -> Unit = {}, val onSalary: () -> Unit = {},
    val onAnalytics: () -> Unit = {}, val onHome: () -> Unit = {}, val onExpenses: () -> Unit = {}, val onGoals: () -> Unit = {},
    val onPreviousMonth: () -> Unit = {}, val onNextMonth: () -> Unit = {},
    val onShowMonths: (Boolean) -> Unit = {}, val onMonth: (YearMonth) -> Unit = {},
    val onFilter: (IncomeStreamFilter) -> Unit = {}, val onSort: () -> Unit = {},
    val onMenu: (String?) -> Unit = {}, val onEdit: (String) -> Unit = {},
    val onDelete: (String?) -> Unit = {}, val onConfirmDelete: () -> Unit = {},
    val onMarkReceived: (String) -> Unit = {}, val onRetry: () -> Unit = {}
)

/** Stateless screen: month selection, filtering, record actions and totals are owned by the ViewModel. */
@Composable
fun IncomeListScreen(state: IncomeStreamState, actions: IncomeStreamActions = IncomeStreamActions(), modifier: Modifier = Modifier) = FinanceTheme {
    val colors = MaterialTheme.colorScheme
    Scaffold(modifier, containerColor = colors.background, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { IncomeMonthBar(state, actions) },
        bottomBar = {
            PfmsBottomNavBar(PfmsNavTab.INCOME, actions.onHome, {}, actions.onExpenses, actions.onGoals, actions.onAnalytics)
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().testTag("income_sections"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.error != null) item(key = "error") { ErrorBanner(state.error, onRetry = if (state.canRetry) actions.onRetry else null) }
                if (state.isLoading) item(key = "loading") {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading your income…", style = MaterialTheme.typography.bodySmall)
                } else {
                    item(key = "summary") { IncomeStreamSummaryCard(state) }
                    item(key = "sources") { IncomeSourcesCard(state, actions.onAnalytics) }
                }
                item(key = "filters") {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IncomeStreamFilter.entries.forEach { filter ->
                            FilterChip(selected = state.controls.filter == filter, onClick = { actions.onFilter(filter) },
                                label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary))
                        }
                    }
                }
                item(key = "records") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Records", style = MaterialTheme.typography.titleMedium)
                            Surface(shape = RoundedCornerShape(30.dp), color = colors.surfaceVariant) {
                                Text(state.recordCount, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        TextButton(onClick = actions.onSort, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(if (state.controls.oldestFirst) "Oldest first ↕" else "Newest first ↕", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (!state.isLoading && state.rows.isEmpty()) item(key = "empty") {
                    Surface(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, colors.outlineVariant), color = colors.surface) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            FinanceIcon(FinanceSymbol.INCOME)
                            Text(state.emptyTitle, style = MaterialTheme.typography.titleMedium)
                            Text(state.emptyDescription, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            Button(onClick = actions.onAdd) { Text("Add Income") }
                        }
                    }
                }
                items(state.rows, key = { "income-${it.id}" }) { row -> IncomeRecordCard(row, state, actions) }
                item(key = "note") {
                    Text("Unpaid freelance invoices are excluded from realized income. USD totals use the saved LKR conversion; crypto losses reduce the total.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
    state.deleteRecord?.let { record ->
        AlertDialog(onDismissRequest = { if (!state.isSaving) actions.onDelete(null) },
            title = { Text("Delete income record?") },
            text = { Text("Delete ${record.title} (${record.amount})? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = actions.onConfirmDelete, enabled = !state.isSaving) { Text("Delete", color = colors.error) }
            }, dismissButton = { TextButton(onClick = { actions.onDelete(null) }, enabled = !state.isSaving) { Text("Cancel") } })
    }
}

@Composable
private fun IncomeMonthBar(state: IncomeStreamState, actions: IncomeStreamActions) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        Column(Modifier.statusBarsPadding().fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = 632.dp).fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onBack) { FinanceIcon(FinanceSymbol.BACK, description = "Back") }
                Text("Income", Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = actions.onSalary) {
                    Box(Modifier.semantics { contentDescription = "Recurring salary" }) {
                        IncomeCalendarIcon(tint = colors.onSurfaceVariant)
                    }
                }
                IconButton(onClick = actions.onAnalytics) { FinanceIcon(FinanceSymbol.CHART, description = "Income analytics") }
            }
            Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Surface(modifier = Modifier.height(48.dp), shape = RoundedCornerShape(14.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = actions.onPreviousMonth, modifier = Modifier.size(36.dp)) {
                                FinanceIcon(FinanceSymbol.BACK, Modifier.size(16.dp), description = "Previous income month")
                            }
                            TextButton(onClick = { actions.onShowMonths(true) }, modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                                BasicText(state.compactMonthLabel, Modifier.fillMaxWidth().testTag("income_month_label"), maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center),
                                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 12.sp, stepSize = .5.sp))
                            }
                            IconButton(onClick = actions.onNextMonth, modifier = Modifier.size(36.dp)) {
                                FinanceIcon(FinanceSymbol.CHEVRON, Modifier.size(16.dp), description = "Next income month")
                            }
                        }
                    }
                    DropdownMenu(state.controls.monthMenu, { actions.onShowMonths(false) }) {
                        state.monthChoices.forEach { (month, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { actions.onMonth(month) })
                        }
                    }
                }
                Button(onClick = actions.onAdd, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    FinanceIcon(FinanceSymbol.PLUS, Modifier.size(16.dp), tint = colors.onPrimary)
                    Spacer(Modifier.width(5.dp)); Text("Add Income", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
