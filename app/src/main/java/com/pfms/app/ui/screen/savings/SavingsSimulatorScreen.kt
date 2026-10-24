package com.pfms.app.ui.screen.savings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.SavingsSimulatorViewModel
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsSimulatorScreen(onBack: () -> Unit, viewModel: SavingsSimulatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var reductionText by remember { mutableStateOf("0") }
    var plannedText by remember { mutableStateOf("0") }
    Scaffold(topBar = { TopAppBar(title = { Text("What-if savings") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.goal == null -> Text("Create an active savings goal to simulate a plan.")
                else -> {
                    Text("Goal: ${state.goal!!.name}", style = MaterialTheme.typography.titleLarge)
                    Text("Try redirecting part of discretionary spending toward your goal. This is a forecast only.")
                    OutlinedTextField(value = reductionText, onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("[0-9]{0,12}(\\.[0-9]{0,2})?"))) {
                            reductionText = value
                            parseMinor(value)?.let(viewModel::setMonthlyReductionMinor)
                        }
                    }, label = { Text("Monthly spending reduction (LKR)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = plannedText, onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("[0-9]{0,12}(\\.[0-9]{0,2})?"))) {
                            plannedText = value
                            parseMinor(value)?.let(viewModel::setPlannedContributionMinor)
                        }
                    }, label = { Text("Other planned monthly contribution (LKR)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.result?.let { result ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Current saved: ${money(result.currentSavedMinor)}")
                                Text("Potential extra by deadline: ${money(result.potentialExtraByDeadlineMinor)}")
                                Text("Projected goal balance: ${money(result.projectedBalanceAtDeadlineMinor)}")
                                Text("Projected progress: ${result.projectedProgressPercent}%")
                                Text("Remaining shortfall: ${money(result.projectedShortfallMinor)}")
                                Text("Estimated completion: ${result.estimatedCompletionDate ?: "Not estimated"}")
                                if (result.isGoalAlreadyComplete) Text("Goal already completed")
                                if (result.isGoalOverdue) Text("Goal deadline has passed")
                            }
                        }
                    }
                    Text("No contributions or financial records are created by this simulation.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
private fun parseMinor(value: String): Long? = runCatching {
    if (value.isBlank()) return@runCatching 0L
    BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
}.getOrNull()
private fun money(value: Long) = "LKR " + BigDecimal.valueOf(value, 2).toPlainString()
