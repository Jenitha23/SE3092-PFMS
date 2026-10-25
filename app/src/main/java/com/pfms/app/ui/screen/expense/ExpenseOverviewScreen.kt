package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.ExpenseListState

data class ExpenseListActions(val back: () -> Unit, val add: () -> Unit, val quick: () -> Unit,
    val edit: (String) -> Unit, val repeat: (String) -> Unit, val query: (String) -> Unit,
    val category: (String?) -> Unit, val classification: (ExpenseClassification?) -> Unit,
    val month: (String) -> Unit, val moveMonth: (Long) -> Unit, val menu: (String, Boolean) -> Unit,
    val recordMenu: (String?) -> Unit, val delete: (String?) -> Unit, val confirmDelete: () -> Unit,
    val sort: () -> Unit, val clearFilters: () -> Unit, val retry: (() -> Unit)?,
    val categories: (() -> Unit)?, val recurring: (() -> Unit)?, val subscriptions: (() -> Unit)?,
    val home: (() -> Unit)?, val income: (() -> Unit)?, val goals: (() -> Unit)?, val insights: (() -> Unit)?, val spending: (() -> Unit)? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseOverviewScreen(state: ExpenseListState, actions: ExpenseListActions) = FinanceTheme {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background,
        topBar = { Column {
            TopAppBar(title = { Text("Expenses", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = actions.back) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
                actions = { Box {
                    IconButton(onClick = { actions.menu("toolsMenu", true) }, modifier = Modifier.semantics { contentDescription = "Expense tools" }) { ExpenseIcon(ExpenseSymbol.MORE) }
                    DropdownMenu(state.toolsMenu, { actions.menu("toolsMenu", false) }) {
                        listOf("Quick Add" to actions.quick, "Spending Analytics" to actions.spending, "Categories" to actions.categories, "Recurring" to actions.recurring, "Subscriptions" to actions.subscriptions).forEach { (label, callback) ->
                            if (callback != null) DropdownMenuItem(text = { Text(label) }, onClick = { actions.menu("toolsMenu", false); callback() })
                        }
                    }
                } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(14.dp), color = colors.surface) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { actions.moveMonth(-1) }) { FinanceIcon(FinanceSymbol.BACK, Modifier.size(16.dp), description = "Previous expense month") }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { actions.menu("monthMenu", true) }, contentPadding = PaddingValues(0.dp)) { Text(state.monthLabel, style = MaterialTheme.typography.labelLarge) }
                            DropdownMenu(state.monthMenu, { actions.menu("monthMenu", false) }) {
                                state.months.forEach { month -> DropdownMenuItem(text = { Text(month) }, onClick = { actions.month(month) }) }
                            }
                        }
                        IconButton(onClick = { actions.moveMonth(1) }) { FinanceIcon(FinanceSymbol.CHEVRON, Modifier.size(16.dp), description = "Next expense month") }
                    }
                }
                Button(onClick = actions.add, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text("+ Add") }
            }
        } },
        floatingActionButton = { SmallFloatingActionButton(onClick = actions.quick, containerColor = colors.primary, contentColor = colors.onPrimary,
            modifier = Modifier.semantics { contentDescription = "Quick Add" }) { ExpenseIcon(ExpenseSymbol.FLASH, tint = colors.onPrimary) } },
        bottomBar = { if (actions.home != null || actions.income != null) PfmsBottomNavBar(PfmsNavTab.EXPENSES,
            { actions.home?.invoke() }, { actions.income?.invoke() }, {}, actions.goals, { actions.insights?.invoke() }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().testTag("expense_sections"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.error?.let { item("error") { ErrorBanner(it, onRetry = actions.retry) } }
                item("summary") { ExpenseOverviewCard(state) }
                actions.spending?.let { open -> item("analytics") {
                    FinanceDestinationLink("Spending Analytics", "Categories, trends and spending patterns", FinanceSymbol.CHART, open)
                } }
                item("search") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(state.query, actions.query, Modifier.weight(1f), placeholder = { Text("Search expenses…", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { ExpenseIcon(ExpenseSymbol.SEARCH) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = expenseFieldColors())
                        Box {
                            IconButton(onClick = { actions.menu("filterMenu", true) }, modifier = Modifier.semantics { contentDescription = "Filter expenses" }) { ExpenseIcon(ExpenseSymbol.FILTER) }
                            DropdownMenu(state.filterMenu, { actions.menu("filterMenu", false) }) {
                                listOf(null, ExpenseClassification.COMMITTED, ExpenseClassification.DISCRETIONARY).forEach { option ->
                                    DropdownMenuItem(text = { Text(option?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All classifications") }, onClick = { actions.classification(option) })
                                }
                            }
                        }
                    }
                }
                item("categories") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        item { FilterChip(state.category == null, { actions.category(null) }, label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary)) }
                        items(state.categories) { category -> FilterChip(state.category == category, { actions.category(category) }, label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary)) }
                    }
                }
                item("heading") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Recent Transactions", style = MaterialTheme.typography.titleMedium); Text(state.count, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                        TextButton(onClick = actions.sort) { Text(if (state.oldestFirst) "Oldest first ↕" else "Newest first ↕", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                when {
                    state.loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    state.rows.isEmpty() -> item("empty") { FinancePanel {
                        Text(if (state.filtered) "No matching expenses" else "A fresh start for this month", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.filtered) "Try a different search or clear your filters." else "Add your first expense to see your spending here.", style = MaterialTheme.typography.bodyMedium)
                        if (state.filtered) TextButton(onClick = actions.clearFilters) { Text("Clear filters") }
                        else Button(onClick = actions.add) { Text("Add Expense") }
                    } }
                    else -> items(state.rows, key = { it.id }) { ExpenseRecordCard(it, state.deleting, actions) }
                }
            }
        }
    }
    if (state.deleteId != null) AlertDialog(onDismissRequest = { actions.delete(null) }, title = { Text("Delete Expense?") },
        text = { Text("Delete this expense? This action cannot be undone.") },
        confirmButton = { TextButton(onClick = actions.confirmDelete, enabled = !state.deleting) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { actions.delete(null) }, enabled = !state.deleting) { Text("Cancel") } })
}
