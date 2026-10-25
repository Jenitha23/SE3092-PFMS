package com.pfms.app.ui.screen.expense

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
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecurringScheduleCard(row: RecurringRecordState, busy: Boolean, menu: String?, vm: AdvancedExpenseViewModel) {
    val colors = MaterialTheme.colorScheme
    val item = row.template
    Surface(Modifier.fillMaxWidth().testTag("template_${item.id}"), shape = RoundedCornerShape(18.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(Modifier.size(36.dp), shape = RoundedCornerShape(11.dp), color = categoryColor(item.categoryName).copy(alpha = .12f)) {
                    Box(contentAlignment = Alignment.Center) { ExpenseIcon(categorySymbol(item.categoryName), Modifier.size(19.dp), categoryColor(item.categoryName)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text("${row.frequency} · ${row.details}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { vm.recordMenu(item.id) }, modifier = Modifier.size(32.dp), enabled = !busy) {
                        ExpenseIcon(ExpenseSymbol.MORE, Modifier.size(18.dp).semantics { contentDescription = "Actions for ${item.title}" })
                    }
                    DropdownMenu(expanded = menu == item.id, onDismissRequest = { vm.recordMenu(null) }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { vm.openTemplate(false, item) })
                        DropdownMenuItem(text = { Text("Delete", color = colors.error) }, onClick = { vm.requestDelete(item) })
                    }
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                    val amount: @Composable (Modifier) -> Unit = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Scheduled amount", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        FinanceValue(row.amount, Modifier.testTag("template_amount_${item.id}"))
                    } }
                    val date: @Composable (Modifier) -> Unit = { modifier -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExpenseIcon(ExpenseSymbol.CALENDAR, Modifier.size(17.dp), colors.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.dueLabel, style = MaterialTheme.typography.labelMedium)
                            Text(row.relativeDate, style = MaterialTheme.typography.labelSmall, color = if (row.due) colors.primary else colors.onSurfaceVariant)
                        }
                    } }
                    if (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f || row.amount.length > 22) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        amount(Modifier.fillMaxWidth()); date(Modifier.fillMaxWidth())
                    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        amount(Modifier.weight(1.15f)); date(Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ExpenseBadge(row.status)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.openTemplate(false, item) }, enabled = !busy) { Text("Edit", style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = { vm.toggleActive(item) }, enabled = !busy) { Text(if (item.active) "Pause" else "Resume", style = MaterialTheme.typography.labelMedium) }
            }
            if (row.due) Button(onClick = { vm.requestConfirmation(item) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("Confirm Payment") }
        }
    }
}
