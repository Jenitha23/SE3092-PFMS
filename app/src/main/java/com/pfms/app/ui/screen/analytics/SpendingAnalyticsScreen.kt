package com.pfms.app.ui.screen.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.dashboard.CategorySpend
import com.pfms.app.domain.model.dashboard.MonthlySummary
import com.pfms.app.domain.model.dashboard.MonthlyTrendPoint
import com.pfms.app.domain.model.dashboard.SpendingInsights
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.ui.component.CategoryDonutChart
import com.pfms.app.ui.component.EmptyState
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.IncomeExpenseTrendChart
import com.pfms.app.ui.component.MonthSelector
import com.pfms.app.ui.component.SplitBar
import com.pfms.app.ui.component.TransactionRow
import com.pfms.app.ui.component.categoryChartColor
import com.pfms.app.ui.component.formatPercent
import com.pfms.app.ui.screen.income.formatAnalyticsLkrAmount
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.SpendingAnalyticsUiState
import com.pfms.app.viewmodel.SpendingAnalyticsViewModel
import java.time.format.TextStyle
import java.util.Locale

/**
 * Spending analytics (FR-30, FR-31, FR-32) for a selectable month:
 *  1. category donut + sorted list with amount and percentage,
 *  2. Committed vs Discretionary split bar,
 *  3. insight card (top three categories, discretionary share, largest category),
 *  4. the five largest individual expenses,
 *  5. six-month income-versus-expense trend (with a text table for accessibility).
 * Every section has a plain empty state, so nothing is blank or divides by zero (FR-34).
 */
@Composable
fun SpendingAnalyticsScreen(
    onBack: () -> Unit,
    onTransactionClick: (Transaction) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenIncomeAnalytics: () -> Unit = {},
    viewModel: SpendingAnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SpendingAnalyticsContent(
        uiState = uiState,
        onBack = onBack,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onRetry = viewModel::retry,
        onTransactionClick = onTransactionClick,
        onOpenHistory = onOpenHistory,
        onOpenIncomeAnalytics = onOpenIncomeAnalytics
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingAnalyticsContent(
    uiState: SpendingAnalyticsUiState,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRetry: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenIncomeAnalytics: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Spending Analytics",
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MonthSelector(
                month = uiState.selectedMonth,
                canGoNext = !uiState.isCurrentMonth,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth
            )

            if (uiState.errorMessage != null) {
                ErrorBanner(message = uiState.errorMessage)
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Try again", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            val summary = uiState.summary
            val hasExpenses = summary != null && summary.totalExpenseMinor > 0L

            CategoryCard(summary, hasExpenses)
            SplitCard(summary, hasExpenses)
            InsightsCard(uiState.insights, hasExpenses)
            TopExpensesCard(uiState.insights?.largestExpenses.orEmpty(), onTransactionClick, onOpenHistory)
            TrendCard(uiState.trend)

            TextButton(
                onClick = onOpenIncomeAnalytics,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Looking for income insights? Open Income Analytics", color = EmeraldPrimary) }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun CategoryCard(summary: MonthlySummary?, hasExpenses: Boolean) {
    AnalyticsCard("Spending by category", "Largest to smallest") {
        if (!hasExpenses || summary == null) {
            EmptyState(
                title = "No spending this month",
                message = "Once you record expenses, your categories appear here."
            )
            return@AnalyticsCard
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryDonutChart(categories = summary.categories) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatAnalyticsLkrAmount(summary.totalExpenseMinor),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        summary.categories.forEachIndexed { index, item ->
            CategoryLegendRow(index, item)
        }
    }
}

@Composable
private fun CategoryLegendRow(index: Int, item: CategorySpend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(categoryChartColor(index), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(
            item.categoryName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatAnalyticsLkrAmount(item.amountMinor),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                formatPercent(item.percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SplitCard(summary: MonthlySummary?, hasExpenses: Boolean) {
    AnalyticsCard("Committed vs Discretionary", "Bills you have to pay compared with choices you make") {
        if (!hasExpenses || summary == null) {
            EmptyState(
                title = "Nothing to compare yet",
                message = "Your split appears after your first expense this month."
            )
        } else {
            SplitBar(split = summary.split)
        }
    }
}

@Composable
private fun InsightsCard(insights: SpendingInsights?, hasExpenses: Boolean) {
    AnalyticsCard("Spending insights") {
        if (!hasExpenses || insights == null) {
            EmptyState(
                title = "No insights yet",
                message = "Add a few expenses and we'll point out where your money goes."
            )
            return@AnalyticsCard
        }
        insights.largestCategory?.let {
            InsightLine("Largest category", "${it.categoryName} \u2022 ${formatAnalyticsLkrAmount(it.amountMinor)}")
        }
        InsightLine("Discretionary share", "${formatPercent(insights.discretionaryPercent)} of this month's spending")
        Spacer(Modifier.height(6.dp))
        Text(
            "Top categories",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        insights.topCategories.forEachIndexed { i, c ->
            Text(
                "${i + 1}. ${c.categoryName} \u2022 ${formatAnalyticsLkrAmount(c.amountMinor)} (${formatPercent(c.percent)})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun InsightLine(label: String, value: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun TopExpensesCard(
    expenses: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    onOpenHistory: () -> Unit
) {
    AnalyticsCard("Five largest expenses") {
        if (expenses.isEmpty()) {
            EmptyState(
                title = "No expenses this month",
                message = "Your biggest purchases will be listed here."
            )
        } else {
            expenses.forEach { TransactionRow(it, onClick = { onTransactionClick(it) }) }
            TextButton(onClick = onOpenHistory, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("See full history \u2192", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TrendCard(points: List<MonthlyTrendPoint>) {
    AnalyticsCard("Income vs expenses", "Last six months") {
        if (points.isEmpty() || points.all { it.incomeMinor == 0L && it.expenseMinor == 0L }) {
            EmptyState(
                title = "No trend yet",
                message = "Your six-month trend builds as you record income and expenses. Months with fewer entries may look partial."
            )
            return@AnalyticsCard
        }
        IncomeExpenseTrendChart(points)
        Spacer(Modifier.height(12.dp))
        // Exact figures as text so the chart is never the only source of information.
        points.forEach { p ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${p.month.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${p.month.year}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "In ${formatAnalyticsLkrAmount(p.incomeMinor)}  \u2022  Out ${formatAnalyticsLkrAmount(p.expenseMinor)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
