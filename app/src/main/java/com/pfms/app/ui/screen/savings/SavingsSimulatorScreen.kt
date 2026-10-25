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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SavingsSimulatorScreen(onBack: () -> Unit, viewModel: SavingsSimulatorViewModel = hiltViewModel(), onOpenGoals: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.retry() }
    SavingsSimulatorContent(state, onBack, viewModel::setMonthlyReductionMinor, viewModel::setPlannedContributionMinor, onOpenGoals, viewModel::retry)
}

@Composable
fun SavingsSimulatorContent(source: SavingsSimulatorUiState, onBack: () -> Unit, onReduction: (Long) -> Unit,
    onPlanned: (Long) -> Unit, onOpenGoals: () -> Unit = {}, onRetry: () -> Unit = {}) {
    val controller: SavingsSimulatorScreenViewModel = viewModel()
    LaunchedEffect(source) { controller.sync(source) }
    val state by controller.state.collectAsStateWithLifecycle()
    FinanceSecondaryPage("What-if Savings", onBack) {
        when {
            source.isLoading -> FinancePanel {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Loading your recorded savings…", style = MaterialTheme.typography.bodyMedium)
            }
            source.goal == null -> {
                if (source.error != null) ErrorBanner(source.error, onRetry = if (source.canRetry) onRetry else null)
                FinancePanel {
                    FinanceIcon(FinanceSymbol.GOAL, Modifier.size(32.dp))
                    Text(if (source.error == null) "Start with a savings goal" else "Your goal is unavailable", style = MaterialTheme.typography.titleLarge)
                    Text(if (source.error == null) "Create an active savings goal to simulate a plan." else "Open Savings Goals to check or restore your recorded goal.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onOpenGoals, shape = RoundedCornerShape(13.dp)) { Text("Open Savings Goals") }
                }
            }
            else -> {
                FinancePanel {
                    Text(source.goal.name, style = MaterialTheme.typography.titleLarge)
                    Text("Hypothetical scenario", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Explore a monthly reduction in discretionary spending. Forecasts do not change your recorded savings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Goal deadline: ${source.goal.targetDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FinancePanel {
                    Text("Build your scenario", style = MaterialTheme.typography.titleMedium)
                    Text("Amounts repeat monthly. Leave a field blank to use zero; enter a non-negative amount with up to 2 decimal places.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(state.reduction, { controller.edit(it, true, onReduction) }, label = { Text("Monthly spending reduction (LKR)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.reductionError != null, supportingText = state.reductionError?.let { { Text(it) } })
                    OutlinedTextField(state.planned, { controller.edit(it, false, onPlanned) }, label = { Text("Other planned monthly contribution (LKR)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = state.plannedError != null, supportingText = state.plannedError?.let { { Text(it) } })
                }
                source.error?.let { ErrorBanner(it, onRetry = if (source.canRetry) onRetry else null) }
                if (state.canShowResults && state.source == source && source.result != null) {
                    if (source.result.isGoalAlreadyComplete) FinancePanel {
                        Text("Goal already completed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Your recorded savings already cover the target. This scenario adds no future savings to the deadline projection.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (source.result.isGoalOverdue) FinancePanel {
                        Text("Goal deadline has passed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text("Deadline projections add no future savings. A completion date may still be estimated from your hypothetical monthly pace.", style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = VaultGreen) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Projected goal progress", style = MaterialTheme.typography.titleSmall, color = VaultMint)
                            Text(state.progressLabel, style = MaterialTheme.typography.headlineSmall, color = androidx.compose.ui.graphics.Color.White)
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = VaultMint, trackColor = VaultMuted.copy(alpha = .2f))
                            Text("Hypothetical balance at the goal deadline", style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                        }
                    }
                    FinancePanel {
                        Text("Your scenario at a glance", style = MaterialTheme.typography.titleMedium)
                        Text("Extra savings come from the spending reduction. The projected balance also includes other planned monthly contributions.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(buildString {
                            append("As of ${source.result.asOfDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))}")
                            if (!source.result.isGoalAlreadyComplete && !source.result.isGoalOverdue)
                                append(" · Monthly periods to deadline: ${source.result.monthsUntilDeadline}")
                        },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.metrics.forEach { (label, amount) ->
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FinanceValue(amount, Modifier.testTag("simulator_${label}"))
                            if (label == "Currently saved") Text("Actual recorded balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (source.result.estimatedCompletionDate == null && !source.result.isGoalAlreadyComplete)
                            Text("A completion date is not available for this scenario.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (state.reductionError == null && state.plannedError == null && source.error == null) {
                    Text("Updating scenario…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("No contributions or financial records are created by this simulation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
