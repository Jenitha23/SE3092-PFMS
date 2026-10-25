package com.pfms.app.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.domain.model.*
import com.pfms.app.domain.model.dashboard.*
import com.pfms.app.ui.component.*
import com.pfms.app.ui.screen.expense.ExpenseIcon
import com.pfms.app.ui.screen.expense.ExpenseSymbol
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import java.time.LocalDate

@Composable
fun TransactionHistoryScreen(onBack: () -> Unit, onTransactionClick: (Transaction) -> Unit = {},
    viewModel: TransactionHistoryViewModel = hiltViewModel(), onAddIncome: () -> Unit = {}, onAddExpense: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionHistoryContent(state, onBack, onTransactionClick, viewModel::loadMore, viewModel::retry,
        viewModel::setType, viewModel::setDateRange, viewModel::setIncomeSource, viewModel::setExpenseCategory,
        viewModel::setPaymentMethod, viewModel::setClassification, viewModel::clearFilters, onAddIncome, onAddExpense)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryContent(uiState: TransactionHistoryUiState, onBack: () -> Unit, onTransactionClick: (Transaction) -> Unit,
    onLoadMore: () -> Unit, onRetry: () -> Unit, onSetType: (TransactionTypeFilter) -> Unit,
    onSetDateRange: (LocalDate?, LocalDate?) -> Unit, onSetIncomeSource: (String?) -> Unit, onSetExpenseCategory: (String?) -> Unit,
    onSetPaymentMethod: (ExpensePaymentMethod?) -> Unit, onSetClassification: (ExpenseClassification?) -> Unit,
    onClearFilters: () -> Unit, onAddIncome: () -> Unit = {}, onAddExpense: () -> Unit = {}) = FinanceTheme {
    val controller: HistoryScreenViewModel = viewModel()
    LaunchedEffect(uiState) { controller.sync(uiState) }
    val state by controller.state.collectAsStateWithLifecycle()
    val display = state.display
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, topBar = {
        TopAppBar(title = { Text("Transaction History", style = if (LocalDensity.current.fontScale > 1.15f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (uiState.isLoading || display == null) CircularProgressIndicator(Modifier.padding(24.dp))
            else LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().testTag("history_sections"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.errorMessage?.let { item { ErrorBanner(it, onRetry = if (uiState.canRetry) onRetry else null) } }
                item { HistorySummaryPanel(display) }
                item {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                        Text("Newest first", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        TextButton(onClick = { controller.filters(true) }) {
                            ExpenseIcon(ExpenseSymbol.FILTER, Modifier.size(17.dp), colors.primary); Spacer(Modifier.width(6.dp))
                            Text("Filters" + if (uiState.filter.activeCount > 0) " (${uiState.filter.activeCount})" else "")
                        }
                    }
                }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionTypeFilter.entries.forEach { type -> FilterChip(uiState.filter.type == type, { onSetType(type) },
                        label = { Text(when(type) { TransactionTypeFilter.ALL -> "All"; TransactionTypeFilter.INCOME -> "Income"; else -> "Expenses" }) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary)) }
                    if (uiState.filter.isActive) TextButton(onClick = onClearFilters) { Text("Clear all") }
                } }
                if (display.rows.isEmpty()) item { FinancePanel {
                    Text(if (uiState.hasAnyTransactions) "No transactions match these filters" else "No transactions yet", style = MaterialTheme.typography.titleMedium)
                    Text(if (uiState.hasAnyTransactions) "Try a wider date range or clear a filter." else "Your income and expenses will appear here, newest first.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    if (uiState.hasAnyTransactions) TextButton(onClick = onClearFilters) { Text("Clear filters") }
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAddIncome) { Text("Add Income") }; OutlinedButton(onClick = onAddExpense) { Text("Add Expense") }
                    }
                } }
                items(display.rows, key = { "${it.transaction.type}-${it.transaction.id}" }) { row -> FinanceTransactionCard(row) { onTransactionClick(row.transaction) } }
                if (uiState.canLoadMore) item("more") {
                    LaunchedEffect(uiState.visible.size) { onLoadMore() }
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            }
        }
    }
    if (state.filtersOpen) AlertDialog(onDismissRequest = { controller.filters(false) }, title = { Text("Filter transactions", style = MaterialTheme.typography.titleMedium) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Date range", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { controller.picker("start", uiState.filter.startDate) }) { Text("From: ${uiState.filter.startDate ?: "Any"}") }
                OutlinedButton(onClick = { controller.picker("end", uiState.filter.endDate) }) { Text("To: ${uiState.filter.endDate ?: "Any"}") }
                if (uiState.filter.startDate != null || uiState.filter.endDate != null) TextButton(onClick = { onSetDateRange(null, null) }) { Text("Clear dates") }
            }
            if (uiState.filter.type != TransactionTypeFilter.EXPENSE) HistoryFilterChoices("Income source", uiState.incomeSources, uiState.filter.incomeSource, onSetIncomeSource)
            if (uiState.filter.type != TransactionTypeFilter.INCOME) {
                HistoryFilterChoices("Expense category", uiState.expenseCategories, uiState.filter.expenseCategory, onSetExpenseCategory)
                Text("Payment method", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ExpensePaymentMethod.entries.forEach { value ->
                    FilterChip(uiState.filter.paymentMethod == value, { onSetPaymentMethod(if (uiState.filter.paymentMethod == value) null else value) }, label = { Text(value.displayLabel()) })
                } }
                Text("Classification", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ExpenseClassification.entries.forEach { value ->
                    FilterChip(uiState.filter.classification == value, { onSetClassification(if (uiState.filter.classification == value) null else value) }, label = { Text(value.displayLabel()) })
                } }
            }
        } }, confirmButton = { TextButton(onClick = { controller.filters(false) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onClearFilters) { Text("Clear all filters") } })
    if (state.picker != null) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.dateMillis)
        DatePickerDialog(onDismissRequest = { controller.picker(null) }, confirmButton = {
            TextButton(onClick = { controller.select(picker.selectedDateMillis, uiState.filter.startDate, uiState.filter.endDate, onSetDateRange) }, enabled = picker.selectedDateMillis != null) { Text("OK") }
        }, dismissButton = { TextButton(onClick = { controller.picker(null) }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@Composable
private fun HistorySummaryPanel(display: HistoryDisplay) {
    Surface(shape = RoundedCornerShape(20.dp), color = VaultGreen) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(display.count, style = MaterialTheme.typography.titleSmall, color = VaultMint)
            Text("Net recorded cashflow", style = MaterialTheme.typography.bodySmall, color = VaultMuted)
            FinanceValue("Net ${display.net}", Modifier.testTag("history_net"), large = true, color = if (display.negative) CashflowRed else androidx.compose.ui.graphics.Color.White)
            HorizontalDivider(color = VaultMuted.copy(alpha = .2f))
            listOf("Income" to display.income, "Expenses" to display.expense).forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                    FinanceValue(value, Modifier.weight(2f).testTag("history_${label.lowercase()}"), align = TextAlign.End, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChoices(title: String, values: List<String>, selected: String?, change: (String?) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { values.forEach { value ->
        FilterChip(selected == value, { change(if (selected == value) null else value) }, label = { Text(value) })
    } }
}
