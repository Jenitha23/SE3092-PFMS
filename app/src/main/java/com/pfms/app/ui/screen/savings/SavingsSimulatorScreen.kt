package com.pfms.app.ui.screen.savings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*

@Composable
fun SavingsSimulatorScreen(onBack: () -> Unit, viewModel: SavingsSimulatorViewModel = hiltViewModel(), onOpenGoals: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SavingsSimulatorContent(state, onBack, viewModel::setMonthlyReductionMinor, viewModel::setPlannedContributionMinor, onOpenGoals)
}

@Composable
fun SavingsSimulatorContent(source: SavingsSimulatorUiState, onBack: () -> Unit, onReduction: (Long) -> Unit,
    onPlanned: (Long) -> Unit, onOpenGoals: () -> Unit = {}) {
    val controller: SavingsSimulatorScreenViewModel = viewModel()
    LaunchedEffect(source) { controller.sync(source) }
    val state by controller.state.collectAsStateWithLifecycle()
    FinanceSecondaryPage("What-if Savings", onBack) {
        when {
            source.isLoading -> CircularProgressIndicator()
            source.goal == null -> FinancePanel {
                FinanceIcon(FinanceSymbol.GOAL, Modifier.size(32.dp))
                Text("Start with a savings goal", style = MaterialTheme.typography.titleLarge)
                Text(source.error ?: "Create an active savings goal to simulate a plan.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onOpenGoals, shape = RoundedCornerShape(13.dp)) { Text("Open Savings Goals") }
            }
            else -> {
                FinancePanel {
                    Text(source.goal.name, style = MaterialTheme.typography.titleLarge)
                    Text("Explore how a monthly spending reduction could help your goal. These figures are a forecast.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FinancePanel {
                    Text("Build your scenario", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.reduction, { controller.edit(it, true, onReduction) }, label = { Text("Monthly spending reduction (LKR)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.reductionError != null, supportingText = state.reductionError?.let { { Text(it) } })
                    OutlinedTextField(state.planned, { controller.edit(it, false, onPlanned) }, label = { Text("Other planned monthly contribution (LKR)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.plannedError != null, supportingText = state.plannedError?.let { { Text(it) } })
                }
                source.error?.let { ErrorBanner(it) }
                if (state.reductionError == null && state.plannedError == null && source.result != null) {
                    Surface(shape = RoundedCornerShape(20.dp), color = VaultGreen) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Projected goal progress", style = MaterialTheme.typography.titleSmall, color = VaultMint)
                            Text(state.progressLabel, style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = VaultMint, trackColor = VaultMuted.copy(alpha = .2f))
                        }
                    }
                    FinancePanel {
                        Text("Your scenario at a glance", style = MaterialTheme.typography.titleMedium)
                        state.metrics.forEach { (label, amount) ->
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FinanceValue(amount, Modifier.testTag("simulator_${label}"))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (source.result.isGoalAlreadyComplete) Text("Goal already completed", color = MaterialTheme.colorScheme.primary)
                        if (source.result.isGoalOverdue) Text("Goal deadline has passed", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text("No contributions or financial records are created by this simulation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
