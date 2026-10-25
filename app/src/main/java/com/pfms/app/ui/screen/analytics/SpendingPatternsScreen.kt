package com.pfms.app.ui.screen.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.SpendingPatternStatus
import com.pfms.app.domain.model.SpendingIncreaseLevel
import com.pfms.app.domain.model.SpendingPatternInsight
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SpendingPatternsScreen(onBack: () -> Unit, onOpenSavingsSimulator: () -> Unit, viewModel: SpendingPatternsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmed by viewModel.confirmedMonths.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.retry() }
    SpendingPatternsContent(state, confirmed, viewModel::setMonthConfirmed, onBack, onOpenSavingsSimulator, viewModel::retry)
}

@Composable
fun SpendingPatternsContent(state: SpendingPatternsUiState, confirmed: Set<YearMonth>, onConfirmMonth: (YearMonth, Boolean) -> Unit,
    onBack: () -> Unit, onSimulator: () -> Unit, onRetry: () -> Unit = {}) {
    FinanceSecondaryPage("Spending Patterns", onBack) {
        FinancePanel {
            FinanceIcon(FinanceSymbol.CHART, Modifier.size(28.dp))
            Text("Understand your spending rhythm", style = MaterialTheme.typography.titleLarge)
            Text("Compare this month's categories with comparable days in previous months.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(YearMonth.from(state.date).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("Default increase threshold: 30%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Recorded data through ${state.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FinancePanel {
            Text("Choose reliable comparison months", style = MaterialTheme.typography.titleMedium)
            Text("Select months where you recorded expenses consistently. At least two complete history months are needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryFinancePresentation.history(state.date).forEach { choice ->
                Surface(shape = RoundedCornerShape(12.dp), color = if (choice.month in confirmed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().toggleable(choice.month in confirmed, role = Role.Checkbox, onValueChange = { onConfirmMonth(choice.month, it) }).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(choice.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Checkbox(checked = choice.month in confirmed, onCheckedChange = null)
                    }
                }
            }
        }
        when {
            state.isLoading -> FinancePanel(Modifier.testTag("patterns_loading")) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Comparing recorded expenses…", style = MaterialTheme.typography.bodyMedium)
            }
            state.error != null -> ErrorBanner(state.error, onRetry = if (state.canRetry) onRetry else null)
            state.result == null -> FinancePanel {
                Text("No comparison available", style = MaterialTheme.typography.titleMedium)
                Text("Choose your recorded history and try loading the comparison again.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            state.result?.status == SpendingPatternStatus.INSUFFICIENT_HISTORY -> FinancePanel {
                Text("Build a dependable comparison", style = MaterialTheme.typography.titleMedium)
                Text("Choose at least two sufficiently complete past months to avoid misleading comparisons.", style = MaterialTheme.typography.bodyMedium)
                Text("${state.result.eligibleHistoryMonths} of 2 required months confirmed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            state.result.insights.isEmpty() -> FinancePanel {
                Text("No significant increases detected", style = MaterialTheme.typography.titleMedium)
                Text("No comparable categories met the detector's increase and minimum-amount rules in the recorded data.", style = MaterialTheme.typography.bodyMedium)
                Text("Categories without a positive historical baseline are not rated. An empty comparison does not establish a spending trend.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> state.result.insights.forEach { insight ->
                SpendingPatternCard(insight, state.date.dayOfMonth)
            }
        }
        FinanceDestinationLink("Explore savings impact", "Try a What-if plan for your savings goal", FinanceSymbol.GOAL, onSimulator)
        Text("Patterns describe recorded spending, not whether a purchase was good or bad.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Only categories with a positive historical baseline are compared.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpendingPatternCard(insight: SpendingPatternInsight, day: Int) {
    val row = SecondaryFinancePresentation.pattern(insight)
    val colors = MaterialTheme.colorScheme
    val high = row.level == SpendingIncreaseLevel.HIGH
    val container = if (high) colors.errorContainer else colors.tertiaryContainer
    val foreground = if (high) colors.onErrorContainer else colors.onTertiaryContainer
    FinancePanel(Modifier.testTag("pattern_${insight.categoryId}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceIcon(FinanceSymbol.CHART, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium)
                Text(insight.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Surface(color = container, contentColor = foreground, shape = RoundedCornerShape(10.dp)) {
            Text(row.severity, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
        Text(row.increase, style = MaterialTheme.typography.titleMedium, color = foreground,
            modifier = Modifier.fillMaxWidth().testTag("pattern_${insight.categoryId}_percent"))
        BoxWithConstraints {
            val stacked = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
            if (stacked) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PatternAmount("Current spending", row.current, "pattern_${insight.categoryId}_current")
                PatternAmount("Historical baseline", row.baseline, "pattern_${insight.categoryId}_baseline")
            } else Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PatternAmount("Current spending", row.current, "pattern_${insight.categoryId}_current", Modifier.weight(1f))
                PatternAmount("Historical baseline", row.baseline, "pattern_${insight.categoryId}_baseline", Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = colors.outlineVariant)
        PatternAmount("Increase in spending", row.difference, "pattern_${insight.categoryId}_difference")
        Text(row.comparison, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        if (insight.isMonthToDateComparison) Text("Month to date: historical spending is matched through day $day, rather than compared with full months.",
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun PatternAmount(label: String, amount: String, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FinanceValue(amount, Modifier.testTag(tag))
    }
}
