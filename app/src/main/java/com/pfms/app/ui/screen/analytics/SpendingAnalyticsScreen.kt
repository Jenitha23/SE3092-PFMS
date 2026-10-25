package com.pfms.app.ui.screen.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun SpendingAnalyticsScreen(onBack: () -> Unit, onTransactionClick: (Transaction) -> Unit = {}, onOpenHistory: () -> Unit = {},
    onOpenIncomeAnalytics: () -> Unit = {}, viewModel: SpendingAnalyticsViewModel = hiltViewModel(),
    onAddExpense: () -> Unit = {}, onAddIncome: () -> Unit = {}, onOpenSpendingPatterns: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SpendingAnalyticsContent(state, onBack, viewModel::previousMonth, viewModel::nextMonth, viewModel::retry,
        onTransactionClick, onOpenHistory, onOpenIncomeAnalytics, onAddExpense, onAddIncome, onOpenSpendingPatterns,
        monthlyData = { MonthlyDataIndicator(it) })
}

@Composable
fun SpendingAnalyticsContent(uiState: SpendingAnalyticsUiState, onBack: () -> Unit, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit,
    onRetry: () -> Unit, onTransactionClick: (Transaction) -> Unit, onOpenHistory: () -> Unit, onOpenIncomeAnalytics: () -> Unit,
    onAddExpense: () -> Unit = {}, onAddIncome: () -> Unit = {}, onOpenSpendingPatterns: () -> Unit = {},
    monthlyData: @Composable (YearMonth) -> Unit = {}) {
    val controller: SpendingScreenViewModel = viewModel()
    LaunchedEffect(uiState) { controller.sync(uiState) }
    val display by controller.display.collectAsStateWithLifecycle()
    FinanceSecondaryPage("Spending Analytics", onBack) {
        val data = display
        if (uiState.isLoading || data == null) CircularProgressIndicator()
        else {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousMonth) { FinanceIcon(FinanceSymbol.BACK, description = "Previous spending month") }
                    Text(data.month, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    IconButton(onClick = onNextMonth, enabled = !uiState.isCurrentMonth) { FinanceIcon(FinanceSymbol.CHEVRON, description = "Next spending month") }
                }
            }
            monthlyData(uiState.selectedMonth)
            uiState.errorMessage?.let { ErrorBanner(it, onRetry = if (uiState.canRetry) onRetry else null) }
            SpendingHero(data)
            if (!data.hasExpenses && uiState.errorMessage == null) FinancePanel {
                Text("No expenses this month", style = MaterialTheme.typography.titleMedium)
                Text("Add an expense to see your spending breakdown and insights.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onAddExpense) { Text("Add Expense") }
            }
            FinanceDestinationLink("Smart spending patterns", "Compare categories with your recorded history", FinanceSymbol.CHART, onOpenSpendingPatterns)
            SpendingCategories(data)
            SpendingSplit(data)
            FinancePanel {
                Text("Spending insights", style = MaterialTheme.typography.titleMedium)
                Text(data.largest, style = MaterialTheme.typography.bodyMedium)
                Text("Discretionary share · ${data.discretionaryPercent} of this month's spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FinancePanel {
                Text("Five largest expenses", style = MaterialTheme.typography.titleMedium)
                if (data.topExpenses.isEmpty()) Text("Your biggest purchases will appear here.", style = MaterialTheme.typography.bodyMedium)
                data.topExpenses.forEach { row -> FinanceTransactionCard(row) { onTransactionClick(row.transaction) } }
                TextButton(onClick = onOpenHistory) { Text("See full history →") }
            }
            SpendingTrend(data, onAddIncome)
            FinanceDestinationLink("Transaction History", "All income and expenses, with filters", FinanceSymbol.EXPENSE, onOpenHistory)
            FinanceDestinationLink("Income Analytics", "Explore received earnings and income trends", FinanceSymbol.INCOME, onOpenIncomeAnalytics)
        }
    }
}

@Composable
private fun SpendingHero(data: SpendingDisplay) {
    Surface(shape = RoundedCornerShape(22.dp), color = VaultGreen) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF006C50), VaultGreen))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                FinanceIcon(FinanceSymbol.PIE, Modifier.size(20.dp), VaultMint)
                Text("Monthly spending", style = MaterialTheme.typography.titleSmall, color = VaultMint)
            }
            FinanceValue(data.total, Modifier.testTag("spending_total"), large = true, color = Color.White)
            Text(data.count, style = MaterialTheme.typography.bodySmall, color = VaultMuted)
            HorizontalDivider(color = VaultMuted.copy(alpha = .2f))
            listOf("Net cashflow" to data.net, "Realized income" to data.income).forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                    FinanceValue(value, Modifier.weight(1.6f), compact = true, align = TextAlign.End, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SpendingCategories(data: SpendingDisplay) = FinancePanel {
    Text("Spending by category", style = MaterialTheme.typography.titleMedium)
    Text("Largest to smallest", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val track = MaterialTheme.colorScheme.outlineVariant
        Canvas(Modifier.size(152.dp).semantics { contentDescription = if (data.hasExpenses) "Category spending chart" else "No spending recorded for this month" }) {
            val stroke = 17.dp.toPx()
            val inset = stroke / 2
            val diameter = size.minDimension - stroke
            drawCircle(track, radius = diameter / 2, style = Stroke(stroke))
            data.categories.forEachIndexed { index, category -> drawArc(categoryChartColor(index), category.start, category.sweep, false,
                Offset(inset, inset), Size(diameter, diameter), style = Stroke(stroke)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(108.dp)) {
            Text("Recorded spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceValue(data.total, compact = true, align = TextAlign.Center)
        }
    }
    if (!data.hasExpenses) Text("No spending this month", style = MaterialTheme.typography.bodyMedium)
    data.categories.forEachIndexed { index, row ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(categoryChartColor(index), RoundedCornerShape(4.dp)))
                Text(row.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(row.percent, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FinanceValue(row.amount, Modifier.testTag("spending_category_${row.title}"))
            LinearProgressIndicator(progress = { row.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp), color = categoryChartColor(index),
                trackColor = MaterialTheme.colorScheme.outlineVariant, gapSize = 0.dp, drawStopIndicator = {})
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SpendingSplit(data: SpendingDisplay) = FinancePanel {
    Text("Committed vs Discretionary", style = MaterialTheme.typography.titleMedium)
    Text("Essentials and flexible spending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(progress = { data.committedFraction }, modifier = Modifier.fillMaxWidth().height(7.dp),
        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.secondaryContainer, gapSize = 0.dp, drawStopIndicator = {})
    listOf(Triple("Committed", data.committed, data.committedPercent), Triple("Discretionary", data.discretionary, data.discretionaryPercent)).forEach { (label, value, percent) ->
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(percent, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                FinanceValue(value)
            }
        }
    }
}

@Composable
private fun SpendingTrend(data: SpendingDisplay, addIncome: () -> Unit) = FinancePanel {
    Text("Income vs expenses", style = MaterialTheme.typography.titleMedium)
    Text("Last six months · Recorded values", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val income = MaterialTheme.colorScheme.primary
    val expense = MaterialTheme.colorScheme.error
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (data.noTrend) {
        Text("No trend yet", style = MaterialTheme.typography.titleSmall)
        Text("Income LKR 0.00 • Expenses LKR 0.00. Record income and expenses to build your trend.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = addIncome) { Text("Add Income") }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            data.trend.forEach { point ->
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "${point.month}, income ${point.income}, expenses ${point.expense}" }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                        val baseline = if (data.negativeTrend) size.height * .6f else size.height - 2.dp.toPx()
                        val span = if (data.negativeTrend) size.height * .36f else baseline - 2.dp.toPx()
                        drawLine(grid, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())
                        listOf(point.incomeFraction to income, point.expenseFraction to expense).forEachIndexed { index, (fraction, color) ->
                            val x = size.width * (.12f + index * .42f)
                            val height = abs(fraction) * span
                            if (height == 0f) drawRoundRect(color.copy(alpha = .25f), Offset(x, 2.dp.toPx()), Size(size.width * .3f, baseline - 2.dp.toPx()),
                                CornerRadius(3.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
                            else drawRoundRect(color, Offset(x, if (fraction >= 0) baseline - height else baseline), Size(size.width * .3f, height), CornerRadius(3.dp.toPx()))
                        }
                    }
                    Text(point.month.substringBefore(" "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("● Income", color = income, style = MaterialTheme.typography.labelSmall)
            Text("● Expenses", color = expense, style = MaterialTheme.typography.labelSmall)
        }
        data.trend.forEach { point ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(point.month, style = MaterialTheme.typography.titleSmall)
                    FinanceValue("In · ${point.income}", compact = true, color = income)
                    FinanceValue("Out · ${point.expense}", compact = true, color = expense)
                }
            }
        }
    }
}
