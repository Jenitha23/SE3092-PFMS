package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.FinanceValue
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*
import com.pfms.app.R

@Composable
internal fun ExpenseOverviewCard(state: ExpenseListState) {
    Surface(shape = RoundedCornerShape(20.dp), color = VaultGreen, contentColor = Color.White,
        modifier = Modifier.testTag("expense_summary"), border = BorderStroke(1.dp, VaultMint.copy(alpha = .18f))) {
        Column(Modifier.background(Brush.linearGradient(listOf(VaultGreen, Color(0xFF07543E)))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MONTHLY SPENDING", style = MaterialTheme.typography.labelMedium, color = VaultMuted)
            FinanceValue(state.total, Modifier.testTag("expense_total"), large = true, color = Color.White)
            Text(state.monthTitle, style = MaterialTheme.typography.bodySmall, color = VaultMint)
            BoxWithConstraints {
                val stacked = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.15f || state.slices.any { it.value.length > 22 }
                if (stacked) ExpenseCategoryLegend(state, Modifier.fillMaxWidth(), stacked = true)
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Canvas(Modifier.size(92.dp).padding(7.dp).semantics { contentDescription = "Spending by category" }) {
                    val stroke = 12.dp.toPx()
                    if (state.slices.isEmpty()) drawCircle(VaultMuted.copy(alpha = .25f), style = Stroke(stroke))
                    var start = -90f
                    state.slices.forEach { slice ->
                        drawArc(categoryColor(slice.category), start, (slice.sweep - 1.5f).coerceAtLeast(0f), false, style = Stroke(stroke))
                        start += slice.sweep
                    }
                }
                ExpenseCategoryLegend(state, Modifier.weight(1f), stacked = false)
                }
            }
            HorizontalDivider(color = VaultMint.copy(alpha = .15f))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.15f) 1 else 2) {
                listOf(Triple("Committed", state.committed, state.committedPercent), Triple("Discretionary", state.discretionary, state.discretionaryPercent)).forEach { (label, amount, percent) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                        FinanceValue(amount, color = Color.White)
                        Text(percent, style = MaterialTheme.typography.labelSmall, color = VaultMint)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseCategoryLegend(state: ExpenseListState, modifier: Modifier, stacked: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (state.slices.isEmpty()) Text("Your category breakdown will appear here.", style = MaterialTheme.typography.bodySmall, color = VaultMuted)
        state.slices.forEach { slice ->
            if (stacked) Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(6.dp).background(categoryColor(slice.category), RoundedCornerShape(50)))
                    Text(slice.category, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                    Text(slice.percent, style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                }
                FinanceValue(slice.value, color = Color.White, compact = true)
            } else Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "${slice.category}, ${slice.percent}, ${slice.value}" }) {
                Box(Modifier.size(6.dp).background(categoryColor(slice.category), RoundedCornerShape(50)))
                Text(slice.category, Modifier.weight(.8f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = VaultMuted)
                FinanceValue(slice.value, Modifier.weight(1.2f), color = Color.White, compact = true)
            }
        }
    }
}

@Composable
internal fun ExpenseRecordCard(row: ExpenseRecordRow, deleting: Boolean, actions: ExpenseListActions) {
    val colors = MaterialTheme.colorScheme
    val accent = categoryColor(row.category)
    Surface(onClick = { actions.edit(row.id) }, enabled = !deleting,
        shape = RoundedCornerShape(18.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("expense_record_${row.id}")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
            val stacked = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.15f || row.amount.length > 22
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .1f), modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) { ExpenseIcon(categorySymbol(row.category), Modifier.size(22.dp), tint = accent) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(row.date, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                    if (!stacked) FinanceValue(row.amount, Modifier.weight(1.1f).testTag("expense_amount_${row.id}"), align = TextAlign.End)
                    ExpenseRecordMenu(row, deleting, actions)
                }
                if (stacked) FinanceValue(row.amount, Modifier.testTag("expense_amount_${row.id}"), align = TextAlign.End)
                Row(Modifier.fillMaxWidth().padding(start = if (stacked) 0.dp else 47.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.primaryContainer) {
                        Text(row.classification, Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    }
                    Text(row.detail, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRecordMenu(row: ExpenseRecordRow, deleting: Boolean, actions: ExpenseListActions) {
    Box {
        IconButton(onClick = { actions.recordMenu(row.id) }, enabled = !deleting,
            modifier = Modifier.size(32.dp).semantics { contentDescription = "Actions for ${row.title}" }) {
            Icon(painterResource(R.drawable.ic_income_more), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(row.menu, { actions.recordMenu(null) }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { actions.recordMenu(null); actions.edit(row.id) })
            DropdownMenuItem(text = { Text("Repeat Expense") }, onClick = { actions.recordMenu(null); actions.repeat(row.id) })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { actions.delete(row.id) })
        }
    }
}
