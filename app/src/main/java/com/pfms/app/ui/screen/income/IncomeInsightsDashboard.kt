package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.usecase.analytics.IncomeInsightRange
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.IncomeInsightsScreenState
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IncomeInsightsDashboard(state: IncomeInsightsScreenState, actions: IncomeInsightsActions, modifier: Modifier,
    home: (() -> Unit)?, income: (() -> Unit)?, expenses: (() -> Unit)?, goals: (() -> Unit)?, calendarEnabled: Boolean,
    monthlyData: @Composable (YearMonth) -> Unit, spending: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val display = state.display
    Scaffold(modifier.fillMaxSize().imePadding(), containerColor = colors.background,
        topBar = { TopAppBar(title = { Text("Income Analytics", style = if (LocalDensity.current.fontScale > 1.15f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = actions.back) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
            actions = {
                if (calendarEnabled) IconButton(onClick = actions.openCalendar) { FinanceIcon(FinanceSymbol.CALENDAR, description = "Choose analytics month") }
                IconButton(onClick = actions.openExport, enabled = display != null && !state.source.isLoading && !state.exporting) { FinanceIcon(FinanceSymbol.EXPORT, description = "Export income report") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)) },
        bottomBar = { if (home != null || income != null || expenses != null) PfmsBottomNavBar(PfmsNavTab.INSIGHTS,
            onHomeClick = { home?.invoke() ?: actions.back() }, onIncomeClick = { income?.invoke() }, onExpensesClick = { expenses?.invoke() },
            onGoalsClick = goals, onInsightsClick = {}) }
    ) { padding ->
        if (state.source.isLoading || display == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("insights_sections"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.source.errorMessage != null) item { InsightMessage(state.source.errorMessage, state.source.canRetry, actions.retry, actions.clearError) }
                if (state.exportMessage != null) item { InsightMessage(state.exportMessage, false, {}, actions.dismissExportMessage) }
                if (actions.range != null) item { Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(IncomeInsightRange.MONTH to "1M", IncomeInsightRange.QUARTER to "3M", IncomeInsightRange.HALF_YEAR to "6M", IncomeInsightRange.YEAR to "1Y", IncomeInsightRange.ALL to "All").forEach { (range, label) ->
                            val active = state.source.range == range
                            Surface(onClick = { actions.range.invoke(range) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("insight_range_$label").semantics { selected = active; role = Role.Tab },
                                shape = RoundedCornerShape(11.dp), color = if (active) colors.primary else colors.surfaceVariant) {
                                Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) colors.onPrimary else colors.onSurfaceVariant) }
                            }
                        }
                    }
                } }
                item { Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = actions.previous) { FinanceIcon(FinanceSymbol.BACK, description = "Previous analytics month") }
                        if (calendarEnabled) TextButton(onClick = actions.openCalendar, modifier = Modifier.weight(1f)) { Text(display.month, style = MaterialTheme.typography.titleSmall); Spacer(Modifier.width(6.dp)); FinanceIcon(FinanceSymbol.DOWN, Modifier.size(13.dp)) }
                        else Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(display.month, style = MaterialTheme.typography.titleSmall) }
                        IconButton(onClick = actions.next) { FinanceIcon(FinanceSymbol.CHEVRON, description = "Next analytics month") }
                    }
                } }
                item { monthlyData(state.source.selectedMonth) }
                item { IncomeInsightsHero(display) }
                spending?.let { open -> item { FinanceDestinationLink("Spending Analytics", "Explore expenses, categories and spending patterns", FinanceSymbol.PIE, open) } }
                if (display.emptyPeriod && state.source.errorMessage == null) item { FinancePanel {
                    Text(if (state.source.range == IncomeInsightRange.MONTH) "No income this month" else "No income in this period", style = MaterialTheme.typography.titleMedium)
                    Text("Record received income to build your breakdown and trends.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Button(onClick = actions.addIncome, shape = RoundedCornerShape(12.dp)) { Text("Add Income") }
                } }
                item { InsightWaterfall(display) }
                item { InsightTrendCard(state, actions.trend) }
                item { InsightIncomeMix(display) }
                item { InsightSourceBreakdown(display) }
                item { InsightMetricTiles(display) }
                item { InsightEstimateCard(state, actions) }
                item { OutlinedButton(onClick = actions.openExport, enabled = !state.exporting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    FinanceIcon(FinanceSymbol.EXPORT, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (state.exporting) "Saving report…" else "Export Report · CSV / PDF")
                } }
            }
        }
    }
    if (state.calendar) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.calendarMillis)
        DatePickerDialog(onDismissRequest = actions.closeCalendar, confirmButton = {
            TextButton(onClick = { actions.calendarDate(picker.selectedDateMillis) }, enabled = picker.selectedDateMillis != null) { Text("Select Month") }
        }, dismissButton = { TextButton(onClick = actions.closeCalendar) { Text("Cancel") } }) { DatePicker(picker) }
    }
    if (state.exportMenu) AlertDialog(onDismissRequest = actions.closeExport, title = { Text("Export income report") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save an income summary and historical monthly totals for ${display?.period.orEmpty()}.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = actions.csv, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("Save CSV") }
            OutlinedButton(onClick = actions.pdf, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("Save PDF") }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = actions.closeExport) { Text("Cancel") } })
}

@Composable
private fun InsightMessage(message: String, retry: Boolean, onRetry: () -> Unit, dismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (retry) TextButton(onClick = onRetry) { Text("Retry") }; TextButton(onClick = dismiss) { Text("Dismiss") } }
        }
    }
}
