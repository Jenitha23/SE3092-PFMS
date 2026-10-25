package com.pfms.app.ui.screen.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavingsGoalRedesignScreen(state: SavingsScreenState, actions: SavingsScreenActions, onBack: () -> Unit,
    simulator: () -> Unit, modifier: Modifier, onHome: (() -> Unit)?, onIncome: (() -> Unit)?, onExpenses: (() -> Unit)?, onInsights: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val source = state.source
    Scaffold(modifier.fillMaxSize(), containerColor = colors.background,
        topBar = { TopAppBar(title = { Column {
            Text("Savings Goal", style = MaterialTheme.typography.titleLarge)
            Text("Build wealth, one milestone at a time", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } }, navigationIcon = { IconButton(onClick = onBack) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
            actions = { IconButton(onClick = actions.help) { FinanceIcon(FinanceSymbol.INFO, Modifier.size(21.dp), description = "About savings goals") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)) },
        bottomBar = { if (onHome != null && onIncome != null && onExpenses != null && onInsights != null)
            PfmsBottomNavBar(PfmsNavTab.GOALS, onHome, onIncome, onExpenses, onGoalsClick = {}, onInsightsClick = onInsights) }
    ) { padding ->
        if (source.isLoading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("savings_sections"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (source.errorMessage != null && state.goalDraft == null && state.transactionDraft == null) item {
                    Surface(color = colors.errorContainer, shape = RoundedCornerShape(14.dp)) { Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(source.errorMessage, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
                        Row { if (source.canRetry) TextButton(onClick = actions.retry, enabled = !source.operationInProgress) { Text("Retry") }; TextButton(onClick = actions.clear) { Text("Dismiss") } }
                    } }
                }
                if (source.activeGoal == null) {
                    item { SavingsEmptyHero(state.display, actions.create) }
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Popular Goal Ideas", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = actions.browse) { Text(if (state.ideasExpanded) "Show less" else "Browse All", style = MaterialTheme.typography.labelMedium) }
                    } }
                    item { GoalIdeasGrid(if (state.ideasExpanded) savingsGoalIdeas else savingsGoalIdeas.take(4), actions.idea) }
                    item { SavingsTip("Make saving a habit", "Record each contribution in your goal ledger. Monthly income and expenses help estimate your pace; only recorded savings fund the goal.") }
                } else {
                    if (source.isOverdue) item { FinancePanel {
                        Text("Target Date Passed", style = MaterialTheme.typography.titleMedium, color = colors.error)
                        Text("Update your target date to plan the remaining savings.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        TextButton(onClick = actions.edit) { Text("Update Date") }
                    } }
                    if (source.isCompleted) item { SavingsTip("Goal complete", "You reached your savings target. Archive it when you are ready for your next milestone.", "Archive Goal", actions.archive) }
                    item { ActiveSavingsHero(state, actions) }
                    item { BoxWithConstraints {
                        if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SavingsAction("Add Contribution", actions.contribute, !source.operationInProgress, true, Modifier.fillMaxWidth())
                            SavingsAction("Record Withdrawal", actions.withdraw, !source.operationInProgress, false, Modifier.fillMaxWidth())
                        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SavingsAction("Add Contribution", actions.contribute, !source.operationInProgress, true, Modifier.weight(1f))
                            SavingsAction("Withdraw", actions.withdraw, !source.operationInProgress, false, Modifier.weight(1f))
                        }
                    } }
                    item { FinancePanel {
                        SavingsSectionHeading("Savings pace", FinanceSymbol.CHART)
                        Text(state.display.paceLabel, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                        SavingsMetric("Required each month", state.display.monthly, "goal_monthly_required")
                        SavingsMetric("Contributed this month", state.display.contribution)
                        SavingsMetric("Net savings this month", state.display.net)
                        SavingsMetric("Trailing 3-month average", state.display.average)
                        state.display.shortfall?.let { SavingsMetric("Monthly shortfall", it) }
                        Text(state.display.projection, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        TextButton(onClick = simulator) { Text("Explore What-if") }
                    } }
                    item { FinancePanel {
                        SavingsSectionHeading("Spending & your goal", FinanceSymbol.PIE)
                        Text(state.display.impact, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        SavingsMetric("Discretionary expenses", state.display.discretionary)
                        SavingsMetric("Committed expenses", state.display.committed)
                    } }
                    item { Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Transaction History", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("${state.display.ledger.size} entries", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    } }
                    if (state.display.ledger.isEmpty()) item { FinancePanel {
                        Text("No contributions yet", style = MaterialTheme.typography.titleMedium)
                        Text("Add a contribution to start funding your goal.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        TextButton(onClick = actions.contribute) { Text("Add Contribution") }
                    } }
                    items(state.display.ledger, key = { it.original.id }) { row -> SavingsLedgerCard(row, actions) }
                }
            }
        }
    }
}

@Composable
private fun SavingsEmptyHero(display: SavingsPresentation, create: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().testTag("savings_empty_hero"), shape = RoundedCornerShape(22.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant), shadowElevation = 1.dp) {
        Column(Modifier.background(Brush.linearGradient(listOf(colors.surface, colors.surface, colors.primaryContainer))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.size(48.dp), shape = RoundedCornerShape(16.dp), color = colors.primaryContainer,
                    border = BorderStroke(1.dp, colors.primary.copy(alpha = .12f))) {
                    Box(contentAlignment = Alignment.Center) { FinanceIcon(FinanceSymbol.GOAL, Modifier.size(26.dp), colors.primary) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("YOUR NEXT MILESTONE", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text("No Active Savings Goal", style = MaterialTheme.typography.titleLarge)
                }
            }
            Text("Give your savings a purpose. Set a target and track each contribution toward it.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("savings_empty_stats"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyGoalMetric("Saved so far", display.saved, FinanceSymbol.WALLET, true, Modifier.weight(1f).fillMaxHeight().testTag("empty_goal_saved"))
                EmptyGoalMetric("Progress", "0%", FinanceSymbol.CHART, false, Modifier.weight(1f).fillMaxHeight().testTag("empty_goal_progress"))
            }
            Button(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                FinanceIcon(FinanceSymbol.PLUS, Modifier.size(18.dp), colors.onPrimary); Spacer(Modifier.width(8.dp)); Text("Create Savings Goal")
            }
        }
    }
}

@Composable
private fun EmptyGoalMetric(label: String, value: String, icon: FinanceSymbol, accent: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = if (accent) colors.primaryContainer else colors.surfaceVariant,
        border = BorderStroke(1.dp, if (accent) colors.primary.copy(alpha = .12f) else colors.outlineVariant)) {
        Column(Modifier.fillMaxHeight().padding(12.dp).heightIn(min = 52.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FinanceIcon(icon, Modifier.size(15.dp), if (accent) colors.primary else colors.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.weight(1f))
            FinanceValue(value, color = if (accent) colors.primary else colors.onSurface)
        }
    }
}

@Composable
private fun GoalIdeasGrid(ideas: List<GoalIdea>, select: (String) -> Unit) {
    BoxWithConstraints {
        val single = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.15f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ideas.chunked(if (single) 1 else 2).forEach { pair -> Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { idea -> Surface(onClick = { select(idea.key) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag("goal_idea_${idea.key}"),
                    shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(32.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) {
                                FinanceIcon(when(idea.key) { "safety" -> FinanceSymbol.SHIELD; "tech" -> FinanceSymbol.MONITOR; "travel" -> FinanceSymbol.PLANE; "home" -> FinanceSymbol.HOME; "education" -> FinanceSymbol.BOOK; else -> FinanceSymbol.CAR }, Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
                            } }
                            Spacer(Modifier.weight(1f))
                            Text(idea.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(idea.name, style = MaterialTheme.typography.titleSmall)
                        Text(idea.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("Quick start", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); FinanceIcon(FinanceSymbol.CHEVRON, Modifier.size(14.dp), MaterialTheme.colorScheme.primary) }
                    }
                } }
                if (pair.size == 1 && !single) Spacer(Modifier.weight(1f))
            } }
        }
    }
}

@Composable
internal fun SavingsTip(title: String, text: String, label: String? = null, onClick: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .12f))) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceIcon(FinanceSymbol.BULB, Modifier.size(23.dp), MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (label != null) TextButton(onClick = onClick) { Text(label) }
            }
        }
    }
}

@Composable
private fun ActiveSavingsHero(state: SavingsScreenState, actions: SavingsScreenActions) {
    val display = state.display
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = VaultGreen) {
        Column(Modifier.background(Brush.linearGradient(listOf(VaultDeep, VaultGreen, Color(0xFF07513D)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceIcon(FinanceSymbol.GOAL, Modifier.size(25.dp), VaultMint)
                Text(state.source.activeGoal?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Color.White)
                IconButton(onClick = actions.edit, enabled = !state.source.operationInProgress) { FinanceIcon(FinanceSymbol.EDIT, Modifier.size(18.dp), VaultMint, "Edit Goal") }
            }
            Text("SAVED SO FAR", style = MaterialTheme.typography.labelSmall, color = VaultMuted)
            FinanceValue(display.saved, Modifier.testTag("goal_saved_amount"), large = true, color = Color.White)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("of ${display.target}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                Text(display.progressLabel, style = MaterialTheme.typography.labelMedium, color = VaultMint)
            }
            LinearProgressIndicator(progress = { display.progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = VaultMint, trackColor = Color.White.copy(alpha = .12f))
            Text("Target · ${display.targetDate}", style = MaterialTheme.typography.bodySmall, color = VaultMuted)
            FinanceValue("${display.remaining} remaining", compact = true, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = actions.archive, enabled = !state.source.operationInProgress) { Text("Archive Goal", color = VaultMint, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = actions.delete, enabled = !state.source.operationInProgress) { Text("Delete Goal", color = CashflowRed, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun SavingsAction(label: String, action: () -> Unit, enabled: Boolean, filled: Boolean, modifier: Modifier) {
    if (filled) Button(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(13.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
    else OutlinedButton(onClick = action, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(13.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun SavingsSectionHeading(label: String, icon: FinanceSymbol) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { FinanceIcon(icon, Modifier.size(18.dp), MaterialTheme.colorScheme.primary); Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
internal fun SavingsMetric(label: String, value: String, tag: String = "") {
    BoxWithConstraints {
        if (maxWidth < 290.dp || LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceValue(value, Modifier.testTag(tag), compact = true)
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FinanceValue(value, Modifier.weight(1.15f).testTag(tag), compact = true, align = TextAlign.End)
        }
    }
}

@Composable
private fun SavingsLedgerCard(row: GoalLedgerRow, actions: SavingsScreenActions) = FinancePanel {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val contribution = row.original.transactionType == GoalTransactionType.CONTRIBUTION
        FinanceIcon(if (contribution) FinanceSymbol.PLUS else FinanceSymbol.MINUS, Modifier.size(22.dp), if (contribution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(row.title, style = MaterialTheme.typography.titleSmall)
            FinanceValue(row.amount, Modifier.testTag("goal_transaction_${row.original.id}"), color = if (contribution) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(row.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            row.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column {
            IconButton(onClick = { actions.editTransaction(row.original) }) { FinanceIcon(FinanceSymbol.EDIT, Modifier.size(17.dp), description = "Edit Transaction") }
            IconButton(onClick = { actions.deleteTransaction(row.original.id) }) { FinanceIcon(FinanceSymbol.DELETE, Modifier.size(17.dp), description = "Delete Transaction") }
        }
    }
}
