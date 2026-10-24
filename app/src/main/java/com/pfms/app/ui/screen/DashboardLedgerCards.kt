package com.pfms.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*

@Composable
internal fun DashboardGoalCard(goal: DashboardGoal, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = VaultGreen, border = BorderStroke(1.dp, VaultMint.copy(alpha = 0.3f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FinanceIcon(FinanceSymbol.MONITOR, tint = VaultMint)
                Column(Modifier.weight(1f)) {
                    Text(if (goal.loading) "Loading savings goal" else goal.title,
                        style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(goal.saved, style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                }
                if (goal.hasGoal) Text(goal.percent, color = VaultMint, style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxWidth().height(7.dp),
                color = VaultMint, trackColor = Color.Black.copy(alpha = 0.2f), gapSize = 0.dp, drawStopIndicator = {})
            if (goal.error != null) Text(goal.error, style = MaterialTheme.typography.bodySmall, color = CashflowRed)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goal.target, Modifier.weight(1f), color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                Text(if (goal.hasGoal) "+ Allocate  ›" else "Set goal  ›", style = MaterialTheme.typography.labelLarge,
                    color = VaultMint)
            }
        }
    }
}

/** Kept as a public adapter for callers that already supply the savings ViewModel's calculated state. */
@Composable
fun DashboardSavingsGoalCard(uiState: SavingsGoalUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DashboardGoalCard(DashboardPresentationMapper.goal(uiState), onClick, modifier)
}

@Composable
internal fun DashboardLedgerCard(content: DashboardContentState, actions: DashboardActions,
    modifier: Modifier = Modifier) {
    FinancePanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceIcon(FinanceSymbol.EXPENSE)
            Column(Modifier.weight(1f)) {
                Text("Live Ledger Activity", style = MaterialTheme.typography.titleMedium)
                Text("Latest recorded entries", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = actions.onHistory, contentPadding = PaddingValues(0.dp)) {
                Text("All (${content.ledgerCount})  ›", style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (content.transactions.isEmpty()) {
            Text("No transactions recorded for this month.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content.transactions.forEach { row ->
            val color = when {
                row.pending -> MaterialTheme.colorScheme.onSurfaceVariant
                row.positive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
            Surface(onClick = { actions.onTransaction(row.transaction) }, shape = RoundedCornerShape(12.dp),
                color = Color.Transparent, modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = "${row.title}, ${row.detail}, ${row.amount}, ${row.badge}"
                }) {
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.08f),
                        modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            FinanceIcon(if (row.positive) FinanceSymbol.INCOME else FinanceSymbol.EXPENSE,
                                Modifier.size(20.dp), color)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall)
                        Text(row.detail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.amount, style = MaterialTheme.typography.labelLarge,
                                color = if (row.positive && !row.pending) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface)
                            Surface(color = color.copy(alpha = 0.08f), shape = RoundedCornerShape(5.dp)) {
                                Text(row.badge, color = color, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        }
    }
}
