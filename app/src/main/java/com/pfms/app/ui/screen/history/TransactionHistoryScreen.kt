package com.pfms.app.ui.screen.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.domain.model.dashboard.HistoryFilter
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionTypeFilter
import com.pfms.app.domain.model.dashboard.displayLabel
import com.pfms.app.ui.component.EmptyState
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.TransactionRow
import com.pfms.app.ui.screen.income.formatAnalyticsLkrAmount
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.TransactionHistoryUiState
import com.pfms.app.viewmodel.TransactionHistoryViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Transaction history (FR-33): newest first, loaded a page at a time as the user scrolls, with
 * filters for date range, type, income source, expense category, payment method and classification.
 * Shows how many transactions match and their totals.
 */
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    onTransactionClick: (Transaction) -> Unit = {},
    viewModel: TransactionHistoryViewModel = hiltViewModel(),
    onAddIncome: () -> Unit = {},
    onAddExpense: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionHistoryContent(
        uiState = uiState,
        onBack = onBack,
        onTransactionClick = onTransactionClick,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        onSetType = viewModel::setType,
        onSetDateRange = viewModel::setDateRange,
        onSetIncomeSource = viewModel::setIncomeSource,
        onSetExpenseCategory = viewModel::setExpenseCategory,
        onSetPaymentMethod = viewModel::setPaymentMethod,
        onSetClassification = viewModel::setClassification,
        onClearFilters = viewModel::clearFilters,
        onAddIncome = onAddIncome,
        onAddExpense = onAddExpense
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryContent(
    uiState: TransactionHistoryUiState,
    onBack: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSetType: (TransactionTypeFilter) -> Unit,
    onSetDateRange: (LocalDate?, LocalDate?) -> Unit,
    onSetIncomeSource: (String?) -> Unit,
    onSetExpenseCategory: (String?) -> Unit,
    onSetPaymentMethod: (ExpensePaymentMethod?) -> Unit,
    onSetClassification: (ExpenseClassification?) -> Unit,
    onClearFilters: () -> Unit,
    onAddIncome: () -> Unit = {},
    onAddExpense: () -> Unit = {}
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transaction History",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Back", fontWeight = FontWeight.Bold, color = EmeraldPrimary, fontSize = 15.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.errorMessage != null) {
                item {
                    Column {
                        ErrorBanner(message = uiState.errorMessage)
                        if (uiState.canRetry) TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Try again", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                FilterPanel(
                    filter = uiState.filter,
                    open = filtersOpen,
                    onToggle = { filtersOpen = !filtersOpen },
                    incomeSources = uiState.incomeSources,
                    expenseCategories = uiState.expenseCategories,
                    onSetType = onSetType,
                    onSetDateRange = onSetDateRange,
                    onSetIncomeSource = onSetIncomeSource,
                    onSetExpenseCategory = onSetExpenseCategory,
                    onSetPaymentMethod = onSetPaymentMethod,
                    onSetClassification = onSetClassification,
                    onClearFilters = onClearFilters
                )
            }

            item { SummaryCard(uiState) }

            if (uiState.visible.isEmpty()) {
                item {
                    if (!uiState.hasAnyTransactions) {
                        EmptyState(
                            title = "No transactions yet",
                            message = "Income and expenses you record will be listed here, newest first.",
                            actionLabel = "Add Income",
                            onAction = onAddIncome
                        )
                        TextButton(onClick = onAddExpense, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Add Expense")
                        }
                    } else {
                        EmptyState(
                            title = "No transactions match these filters",
                            message = "Try a wider date range or clear a filter.",
                            actionLabel = "Clear filters",
                            onAction = onClearFilters
                        )
                    }
                }
            } else {
                items(uiState.visible, key = { "${it.type}-${it.id}" }) { tx ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        TransactionRow(
                            transaction = tx,
                            modifier = Modifier.padding(horizontal = 14.dp),
                            onClick = { onTransactionClick(tx) }
                        )
                    }
                }
                if (uiState.canLoadMore) {
                    item(key = "load-more") {
                        // Composed only when scrolled to the end, which triggers the next page.
                        LaunchedEffect(uiState.visible.size) { onLoadMore() }
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EmeraldPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(uiState: TransactionHistoryUiState) {
    val s = uiState.summary
    val noun = if (s.count == 1) "transaction" else "transactions"
    val spoken = "${s.count} matching $noun. Income ${formatAnalyticsLkrAmount(s.incomeMinor)}, " +
            "expenses ${formatAnalyticsLkrAmount(s.expenseMinor)}, net ${formatAnalyticsLkrAmount(s.netMinor)}"
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${s.count} $noun" + if (uiState.filter.isActive) " match" else "",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Income ${formatAnalyticsLkrAmount(s.incomeMinor)}  \u2022  Expenses ${formatAnalyticsLkrAmount(s.expenseMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Net ${formatAnalyticsLkrAmount(s.netMinor)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    filter: HistoryFilter,
    open: Boolean,
    onToggle: () -> Unit,
    incomeSources: List<String>,
    expenseCategories: List<String>,
    onSetType: (TransactionTypeFilter) -> Unit,
    onSetDateRange: (LocalDate?, LocalDate?) -> Unit,
    onSetIncomeSource: (String?) -> Unit,
    onSetExpenseCategory: (String?) -> Unit,
    onSetPaymentMethod: (ExpensePaymentMethod?) -> Unit,
    onSetClassification: (ExpenseClassification?) -> Unit,
    onClearFilters: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                    val count = filter.activeCount
                    Text(
                        (if (open) "Hide filters" else "Filters") + if (count > 0) " ($count)" else "",
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (filter.isActive) {
                    TextButton(onClick = onClearFilters, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Clear all", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            AnimatedVisibility(visible = open) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterLabel("Type")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransactionTypeFilter.values().forEach { t ->
                            FilterChip(
                                selected = filter.type == t,
                                onClick = { onSetType(t) },
                                label = {
                                    Text(
                                        when (t) {
                                            TransactionTypeFilter.ALL -> "All"
                                            TransactionTypeFilter.INCOME -> "Income"
                                            TransactionTypeFilter.EXPENSE -> "Expenses"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    FilterLabel("Date range")
                    DateRangeRow(filter.startDate, filter.endDate, onSetDateRange)

                    if (filter.type != TransactionTypeFilter.EXPENSE) {
                        FilterLabel("Income source")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            incomeSources.forEach { source ->
                                FilterChip(
                                    selected = filter.incomeSource == source,
                                    onClick = { onSetIncomeSource(if (filter.incomeSource == source) null else source) },
                                    label = { Text(source) }
                                )
                            }
                        }
                    }

                    if (filter.type != TransactionTypeFilter.INCOME) {
                        if (expenseCategories.isNotEmpty()) {
                            FilterLabel("Expense category")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                expenseCategories.forEach { cat ->
                                    FilterChip(
                                        selected = filter.expenseCategory == cat,
                                        onClick = { onSetExpenseCategory(if (filter.expenseCategory == cat) null else cat) },
                                        label = { Text(cat) }
                                    )
                                }
                            }
                        }

                        FilterLabel("Payment method")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpensePaymentMethod.values().forEach { method ->
                                FilterChip(
                                    selected = filter.paymentMethod == method,
                                    onClick = { onSetPaymentMethod(if (filter.paymentMethod == method) null else method) },
                                    label = { Text(method.displayLabel()) }
                                )
                            }
                        }

                        FilterLabel("Classification")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExpenseClassification.values().forEach { c ->
                                FilterChip(
                                    selected = filter.classification == c,
                                    onClick = { onSetClassification(if (filter.classification == c) null else c) },
                                    label = { Text(c.displayLabel()) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private enum class PickerTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeRow(
    start: LocalDate?,
    end: LocalDate?,
    onSetDateRange: (LocalDate?, LocalDate?) -> Unit
) {
    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { picker = PickerTarget.START },
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("From: ${start?.toString() ?: "Any"}") }
        OutlinedButton(
            onClick = { picker = PickerTarget.END },
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text("To: ${end?.toString() ?: "Any"}") }
        if (start != null || end != null) {
            TextButton(onClick = { onSetDateRange(null, null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Clear")
            }
        }
    }

    val target = picker
    if (target != null) {
        val initial = (if (target == PickerTarget.START) start else end)
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                Button(
                    onClick = {
                        val picked = state.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        if (target == PickerTarget.START) onSetDateRange(picked, end)
                        else onSetDateRange(start, picked)
                        picker = null
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}
