package com.pfms.app.ui.screen.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.SpendingPatternStatus
import com.pfms.app.viewmodel.SpendingPatternsViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingPatternsScreen(
    onBack: () -> Unit,
    onOpenSavingsSimulator: () -> Unit,
    viewModel: SpendingPatternsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmed by viewModel.confirmedMonths.collectAsStateWithLifecycle()
    val month = YearMonth.from(state.date)
    Scaffold(topBar = { TopAppBar(title = { Text("Spending patterns") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compare this month's category spending with comparable days in previous months.", style = MaterialTheme.typography.bodyMedium)
            Text("Select only months where you recorded your expenses consistently:", style = MaterialTheme.typography.titleSmall)
            (1..3).forEach { offset ->
                val past = month.minusMonths(offset.toLong())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(past.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                    Checkbox(checked = past in confirmed, onCheckedChange = { viewModel.setMonthConfirmed(past, it) })
                }
            }
            when {
                state.isLoading -> CircularProgressIndicator()
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
                state.result?.status == SpendingPatternStatus.INSUFFICIENT_HISTORY ->
                    Text("Choose at least two sufficiently complete past months to avoid misleading comparisons.")
                state.result?.insights.isNullOrEmpty() -> Text("No unusual category increases detected for this comparison.")
                else -> state.result!!.insights.forEach { insight ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(insight.categoryName, style = MaterialTheme.typography.titleMedium)
                            Text("${insight.increasePercent}% above your previous spending baseline")
                            Text("Current: ${money(insight.currentAmountMinor)}  ·  Baseline: ${money(insight.baselineAmountMinor)}")
                            Text("Difference: ${money(insight.differenceMinor)}")
                            Text("Compared against ${insight.comparisonMonths} months; ${if (insight.isMonthToDateComparison) "month-to-date" else "complete month"} comparison.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onOpenSavingsSimulator) { Text("Explore savings impact") }
                        }
                    }
                }
            }
            Text("Patterns describe recorded spending, not whether a purchase was good or bad.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
private fun money(minor: Long) = "LKR " + java.math.BigDecimal.valueOf(minor, 2).toPlainString()
