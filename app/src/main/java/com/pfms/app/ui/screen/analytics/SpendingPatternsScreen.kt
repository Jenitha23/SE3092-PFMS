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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.SpendingPatternStatus
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*
import java.time.YearMonth

@Composable
fun SpendingPatternsScreen(onBack: () -> Unit, onOpenSavingsSimulator: () -> Unit, viewModel: SpendingPatternsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmed by viewModel.confirmedMonths.collectAsStateWithLifecycle()
    SpendingPatternsContent(state, confirmed, viewModel::setMonthConfirmed, onBack, onOpenSavingsSimulator)
}

@Composable
fun SpendingPatternsContent(state: SpendingPatternsUiState, confirmed: Set<YearMonth>, onConfirmMonth: (YearMonth, Boolean) -> Unit,
    onBack: () -> Unit, onSimulator: () -> Unit) {
    FinanceSecondaryPage("Spending Patterns", onBack) {
        FinancePanel {
            FinanceIcon(FinanceSymbol.CHART, Modifier.size(28.dp))
            Text("Understand your spending rhythm", style = MaterialTheme.typography.titleLarge)
            Text("Compare this month's categories with comparable days in previous months.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Current month · ${YearMonth.from(state.date)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
            state.isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error != null -> ErrorBanner(state.error)
            state.result?.status == SpendingPatternStatus.INSUFFICIENT_HISTORY -> FinancePanel {
                Text("Build a dependable comparison", style = MaterialTheme.typography.titleMedium)
                Text("Choose at least two sufficiently complete past months to avoid misleading comparisons.", style = MaterialTheme.typography.bodyMedium)
            }
            state.result?.insights.isNullOrEmpty() -> FinancePanel {
                Text("A steady spending pattern", style = MaterialTheme.typography.titleMedium)
                Text("No unusual category increases detected for this comparison.", style = MaterialTheme.typography.bodyMedium)
            }
            else -> state.result?.insights.orEmpty().forEach { insight ->
                val row = SecondaryFinancePresentation.pattern(insight)
                FinancePanel {
                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                    Text(row.increase, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    listOf("Current spending" to row.current, "Previous baseline" to row.baseline, "Difference" to row.difference).forEach { (label, amount) ->
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FinanceValue(amount)
                    }
                    Text(row.comparison, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FinanceDestinationLink("Explore savings impact", "Try a What-if plan for your savings goal", FinanceSymbol.GOAL, onSimulator)
        Text("Patterns describe recorded spending, not whether a purchase was good or bad.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
