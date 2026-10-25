package com.pfms.app.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.screen.expense.ExpenseIcon
import com.pfms.app.ui.screen.expense.categorySymbol
import com.pfms.app.viewmodel.FinanceTransactionDisplay

@Composable
fun FinanceTransactionCard(row: FinanceTransactionDisplay, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = if (row.pending) colors.onSurfaceVariant else if (row.positive) colors.primary else colors.error
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("transaction_${row.transaction.type}_${row.transaction.id}")
        .semantics(mergeDescendants = true) { contentDescription = "${row.title}, ${row.date}, ${row.amount}, ${row.badge}" },
        shape = RoundedCornerShape(18.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant)) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            val stacked = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Surface(Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = .08f)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (row.income) FinanceIcon(FinanceSymbol.INCOME, Modifier.size(19.dp), accent)
                        else ExpenseIcon(categorySymbol(row.category), Modifier.size(19.dp), accent)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall)
                            Text(row.date, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        if (!stacked) FinanceValue(row.amount, Modifier.weight(1.15f).testTag("transaction_amount_${row.transaction.id}"), compact = true, align = TextAlign.End, color = accent)
                    }
                    if (stacked) FinanceValue(row.amount, Modifier.testTag("transaction_amount_${row.transaction.id}"), color = accent)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(5.dp), color = accent.copy(alpha = .08f)) {
                            Text(row.badge, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = accent)
                        }
                        if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
