package com.pfms.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import java.time.YearMonth

@Composable
internal fun FinancePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun DashboardCycleStrip(content: DashboardContentState?) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.055f),
        border = BorderStroke(1.dp, VaultMuted.copy(alpha = 0.15f))) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text("●  ${content?.cycle ?: "YOUR MONTHLY OVERVIEW"}",
                style = MaterialTheme.typography.labelSmall, color = VaultMuted)
            Surface(shape = RoundedCornerShape(50), color = VaultMint.copy(alpha = 0.12f)) {
                Text(content?.remaining ?: "Loading", color = VaultMint,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
internal fun DashboardCashflowCard(state: DashboardUiState, chrome: DashboardChromeState,
                                   months: List<Pair<YearMonth, String>>, actions: DashboardActions) {
    val content = state.content
    Surface(shape = RoundedCornerShape(26.dp), color = VaultGreen,
        border = BorderStroke(1.dp, VaultMint.copy(alpha = 0.25f)), shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically) {
                Text("CASH FLOW OVERVIEW", color = VaultMuted, style = MaterialTheme.typography.labelMedium)
                Box {
                    TextButton(onClick = actions.onShowMonths,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Text(state.monthLabel, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp)); FinanceIcon(FinanceSymbol.DOWN, Modifier.size(14.dp), VaultMint)
                    }
                    DropdownMenu(chrome.monthMenuExpanded, actions.onDismissMonths) {
                        DropdownMenuItem(text = { Text("← Previous month") }, onClick = {
                            actions.onPreviousMonth(); actions.onDismissMonths()
                        })
                        DropdownMenuItem(text = { Text("Next month →") }, enabled = !state.isCurrentMonth, onClick = {
                            actions.onNextMonth(); actions.onDismissMonths()
                        })
                        HorizontalDivider()
                        months.forEach { (month, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { actions.onSelectMonth(month) })
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${content?.monthName ?: "MONTHLY"} NET CASH FLOW",
                    color = VaultMuted, style = MaterialTheme.typography.labelMedium)
                Text(content?.net ?: "—", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                val statusColor = if (content?.deficit == true) CashflowRed else VaultMint
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))) {
                    Text("●  ${content?.status ?: "Loading recorded totals"}", color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                CashflowMetric("REALIZED INFLOW", content?.income ?: "—", content?.growth ?: "Loading",
                    VaultMint, actions.onIncome, Modifier.weight(1f).fillMaxHeight())
                CashflowMetric("TOTAL OUTFLOW", content?.expenses ?: "—", content?.expenseRatio ?: "Loading",
                    CashflowRed, actions.onExpenses, Modifier.weight(1f).fillMaxHeight())
            }
            HorizontalDivider(color = VaultMuted.copy(alpha = 0.15f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Inflow-to-outflow coverage", Modifier.weight(1f), color = VaultMuted,
                        style = MaterialTheme.typography.bodySmall)
                    Text(content?.coverage ?: "—", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(progress = { content?.coverageProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp), color = VaultMint,
                    trackColor = Color.Black.copy(alpha = 0.25f), gapSize = 0.dp, drawStopIndicator = {})
                Text(content?.coverageNote ?: "Totals use your recorded entries", style = MaterialTheme.typography.bodySmall,
                    color = if (content?.deficit == true) CashflowRed else VaultMuted)
                HorizontalDivider(color = VaultMuted.copy(alpha = 0.15f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Net savings goal contributions", Modifier.weight(1f),
                        color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                    Text(content?.savingsAllocation ?: "—", color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remaining after goal savings", Modifier.weight(1f),
                        color = VaultMuted, style = MaterialTheme.typography.bodySmall)
                    Text(content?.availableAfterSavings ?: "—", color = VaultMint,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun CashflowMetric(label: String, amount: String, note: String, accent: Color,
                           onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f), border = BorderStroke(1.dp, VaultMuted.copy(alpha = 0.17f))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("● $label · LKR", color = accent, style = MaterialTheme.typography.labelSmall)
            Text(amount, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(note, color = VaultMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun DashboardQuickActions(actions: DashboardActions) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            QuickAction("Inflow", FinanceSymbol.PLUS, false, actions.onAddIncome, Modifier.weight(1f))
            QuickAction("Quick Add", FinanceSymbol.BOLT, true, actions.onQuickAdd, Modifier.weight(1.15f))
            QuickAction("Expense", FinanceSymbol.MINUS, false, actions.onAddExpense, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: FinanceSymbol, primary: Boolean,
                        onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(13.dp), modifier = modifier,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically) {
            FinanceIcon(icon, Modifier.size(16.dp),
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else if (icon == FinanceSymbol.MINUS)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center, color = if (primary) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface)
        }
    }
}
