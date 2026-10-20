package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.dashboard.MonthlyFinanceCalculator
import com.pfms.app.domain.usecase.analytics.IncomeRealizedCalculator
import com.pfms.app.domain.usecase.SafeCalculations
import com.pfms.app.ui.component.formatPercent
import com.pfms.app.ui.component.HomeNavIcon
import com.pfms.app.ui.component.IncomeNavIcon
import com.pfms.app.ui.component.ExpensesNavIcon
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ExpenseListScreen(
    expenses: List<Expense>, isLoading: Boolean, isDeleting: Boolean,
    onAddExpense: () -> Unit, onQuickAdd: () -> Unit, onEditExpense: (String) -> Unit,
    onDeleteExpense: (String) -> Unit, onBack: () -> Unit, errorMessage: String? = null,
    onRepeatExpense: (String) -> Unit = {}, onRetry: () -> Unit = {},
    onCategories: (() -> Unit)? = null, onRecurring: (() -> Unit)? = null,
    onSubscriptions: (() -> Unit)? = null, onHome: (() -> Unit)? = null, onIncome: (() -> Unit)? = null,
    onGoals: (() -> Unit)? = null, onInsights: (() -> Unit)? = null
) = ExpenseUiTheme {
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var classification by rememberSaveable { mutableStateOf<ExpenseClassification?>(null) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now(ZoneId.of("Asia/Colombo")).toString()) }
    var monthMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var toolsMenu by remember { mutableStateOf(false) }
    val months = remember(expenses) {
        (expenses.mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it.date)).toString() }.getOrNull() } +
            YearMonth.now(ZoneId.of("Asia/Colombo")).toString()).distinct().sortedDescending()
    }
    val monthly = remember(expenses, month) {
        expenses.filter { IncomeRealizedCalculator.parseYearMonth(it.date)?.toString() == month }
    }
    val categories = remember(monthly) { monthly.map { it.categoryName }.distinct().sorted() }
    val filtered = remember(monthly, query, category, classification) {
        monthly.filter { item ->
            (category == null || item.categoryName == category) &&
                (classification == null || item.classification == classification) &&
                (query.isBlank() || listOf(item.merchant, item.note, item.categoryName, item.date,
                    item.paymentMethod.name.replace('_', ' ')).any { it.contains(query.trim(), true) })
        }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp).semantics { contentDescription = "Back" }) { ExpenseIcon(ExpenseSymbol.BACK) }
                        Text("Expenses", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Quick Add Button: Highlighted primary action
                        Button(
                            onClick = onQuickAdd,
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F766E),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            ExpenseIcon(ExpenseSymbol.FLASH, Modifier.size(15.dp), tint = Color.White)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Quick Add",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Full Form Add Button
                        OutlinedButton(
                            onClick = onAddExpense,
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 9.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            ExpenseIcon(ExpenseSymbol.ADD, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Add",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box {
                            IconButton(onClick = { toolsMenu = true }, modifier = Modifier.size(34.dp).semantics { contentDescription = "Expense tools" }) {
                                ExpenseIcon(ExpenseSymbol.MORE, Modifier.size(18.dp))
                            }
                            DropdownMenu(toolsMenu, { toolsMenu = false }) {
                                listOf(
                                    "Quick Add" to onQuickAdd,
                                    "Categories" to onCategories,
                                    "Recurring" to onRecurring,
                                    "Subscriptions" to onSubscriptions
                                ).forEach { (label, action) ->
                                    if (action != null) DropdownMenuItem(text = { Text(label) }, onClick = { toolsMenu = false; action() })
                                }
                            }
                        }
                    }
                }
                Text("Track your spending and stay in control.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickAdd,
                containerColor = Color(0xFF0F766E),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                icon = {
                    ExpenseIcon(ExpenseSymbol.FLASH, Modifier.size(18.dp), tint = Color.White)
                },
                text = {
                    Text(
                        "Quick Add",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            if (onHome != null || onIncome != null) {
                com.pfms.app.ui.component.PfmsBottomNavBar(
                    selectedTab = com.pfms.app.ui.component.PfmsNavTab.EXPENSES,
                    onHomeClick = { onHome?.invoke() },
                    onIncomeClick = { onIncome?.invoke() },
                    onExpensesClick = { /* Already on Expenses */ },
                    onGoalsClick = onGoals,
                    onInsightsClick = { onInsights?.invoke() }
                )
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item("summary") {
                ExpensePanel {
                    Box {
                        TextButton(onClick = { monthMenu = true }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(0.dp)) {
                            ExpenseIcon(ExpenseSymbol.CALENDAR)
                            Spacer(Modifier.width(8.dp))
                            Text(YearMonth.parse(month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(8.dp)); ExpenseIcon(ExpenseSymbol.CHEVRON, Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                            months.forEach { option -> DropdownMenuItem(text = {
                                Text(YearMonth.parse(option).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)))
                            }, onClick = { month = option; category = null; monthMenu = false }) }
                        }
                    }
                    MonthlySpendingOverview(monthly)
                }
            }
            item("search") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Search expenses…", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { ExpenseIcon(ExpenseSymbol.SEARCH) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                    Box {
                        OutlinedIconButton(onClick = { filterMenu = true }, modifier = Modifier.size(52.dp).semantics { contentDescription = "Filter expenses" },
                            shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            ExpenseIcon(ExpenseSymbol.FILTER, tint = if (classification == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(filterMenu, { filterMenu = false }) {
                            listOf(null, ExpenseClassification.COMMITTED, ExpenseClassification.DISCRETIONARY).forEach { value ->
                                DropdownMenuItem(text = { Text(value?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "All classifications") },
                                    onClick = { classification = value; filterMenu = false })
                            }
                        }
                    }
                }
            }
            item("categories") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf<String?>(null) + categories) { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option ?: "All") }, shape = RoundedCornerShape(50),
                            border = null, colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                    }
                }
            }
            if (errorMessage != null) item("error") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            item("transactions-title") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Transactions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${filtered.size} ${if (filtered.size == 1) "expense" else "expenses"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                isLoading -> item("loading") { Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                filtered.isEmpty() -> item("empty") {
                    ExpensePanel {
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { CategoryAvatar("Expenses") }
                        Text(if (query.isNotBlank() || category != null || classification != null) "No matching expenses" else "A fresh start for this month",
                            Modifier.align(Alignment.CenterHorizontally), fontWeight = FontWeight.SemiBold)
                        Text(if (query.isNotBlank() || category != null || classification != null) "Try a different search or clear your filters." else "Add your first expense to see your spending here.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (query.isNotBlank() || category != null || classification != null) {
                            TextButton(onClick = { query = ""; category = null; classification = null }) { Text("Clear filters") }
                        } else {
                            Button(onClick = onAddExpense) { Text("Add Expense") }
                        }
                    }
                }
                else -> items(filtered, key = { "expense-${it.id}" }) { expense ->
                    ExpenseTransactionCard(expense, isDeleting, { onEditExpense(expense.id) }, { onRepeatExpense(expense.id) }, { expenseToDelete = expense })
                }
            }
        }
    }
    expenseToDelete?.let { expense ->
        AlertDialog(onDismissRequest = { if (!isDeleting) expenseToDelete = null }, title = { Text("Delete Expense?") },
            text = { Text("Delete this expense? This action cannot be undone.") },
            confirmButton = { TextButton(enabled = !isDeleting, onClick = { expenseToDelete = null; onDeleteExpense(expense.id) }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !isDeleting, onClick = { expenseToDelete = null }) { Text("Cancel") } })
    }
}

@Composable
private fun MonthlySpendingOverview(expenses: List<Expense>) {
    val split = remember(expenses) { MonthlyFinanceCalculator.split(expenses) }
    val total = BigDecimal.valueOf(split.totalMinor, 2)
    val slices = remember(expenses) { MonthlyFinanceCalculator.categoryBreakdown(expenses, visibleCategories = 4) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val emptyColor = MaterialTheme.colorScheme.outlineVariant
        Box(Modifier.size(104.dp).semantics { contentDescription = "Spending by category. Total LKR ${expenseMoney(total)}" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(7.dp)) {
                val stroke = 14.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                if (total.signum() <= 0) drawArc(emptyColor, 0f, 360f, false, Offset(stroke/2, stroke/2), arcSize, style = Stroke(stroke))
                else slices.forEach { (name, value) ->
                    val sweep = (SafeCalculations.ratio(value.coerceAtLeast(0L).toDouble(), split.totalMinor.toDouble()) * 360).toFloat()
                    drawArc(categoryColor(name), start, (sweep - 1.5f).coerceAtLeast(0f), false, Offset(stroke/2, stroke/2), arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                Text("Total", fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("LKR", fontSize = 10.sp, lineHeight = 12.sp)
                Text(expenseMoney(total), fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            if (slices.isEmpty()) Text("Your category breakdown will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            slices.forEach { (name, value) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).background(categoryColor(name), RoundedCornerShape(50)))
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(expenseMoney(BigDecimal.valueOf(value, 2)), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ExpenseClassification.entries.forEach { type ->
            val amount = BigDecimal.valueOf(if (type == ExpenseClassification.COMMITTED) split.committedMinor else split.discretionaryMinor, 2)
            val color = if (type == ExpenseClassification.COMMITTED) ExpenseOrange else ExpensePurple
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.08f), border = BorderStroke(1.dp, color.copy(alpha = 0.15f))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val percent = if (type == ExpenseClassification.COMMITTED) split.committedPercent else split.discretionaryPercent
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (type == ExpenseClassification.COMMITTED) "Committed" else "Discretionary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatPercent(percent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("LKR ${expenseMoney(amount)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ExpenseTransactionCard(expense: Expense, isDeleting: Boolean, onEdit: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(start = 12.dp, top = 14.dp, bottom = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CategoryAvatar(expense.categoryName)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(expense.merchant.ifBlank { expense.categoryName }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(runCatching { LocalDate.parse(expense.date).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) }.getOrDefault(expense.date),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExpenseBadge(expense.categoryName, categoryColor(expense.categoryName))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${expense.currency} ${expenseMoney(expense.amountMinor)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpenseIcon(ExpenseSymbol.CARD, Modifier.size(14.dp))
                    Text(when (expense.paymentMethod) { ExpensePaymentMethod.CASH -> "Cash"; ExpensePaymentMethod.CARD -> "Card"; ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT -> "Bank transfer"; ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM -> "Wallet" },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp).semantics { contentDescription = "Actions for ${expense.merchant.ifBlank { expense.categoryName }}" }) { ExpenseIcon(ExpenseSymbol.MORE, Modifier.size(18.dp)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Repeat") }, onClick = { menu = false; onRepeat() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, enabled = !isDeleting, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
