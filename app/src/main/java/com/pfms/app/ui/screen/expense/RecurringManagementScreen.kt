package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.*
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.*
import com.pfms.app.viewmodel.*

@Composable
internal fun RecurringManagementScreen(state: AdvancedExpenseScreenState, busy: Boolean, message: String?, retry: Boolean,
    subscriptions: Boolean, vm: AdvancedExpenseViewModel, onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rows = if (subscriptions) state.subscriptions else state.filteredRecurring
    Scaffold(containerColor = colors.background,
        topBar = { AdvancedExpenseHeader(if (subscriptions) "Subscriptions" else "Recurring Expenses",
            if (subscriptions) "Keep renewals in view" else "Plan your regular payments", onBack,
            action = { Button(onClick = { vm.openTemplate(subscriptions) }, enabled = !busy,
                modifier = Modifier.padding(end = 16.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp)) { Text("+ New") } }) },
        bottomBar = { if (!subscriptions) Surface(color = colors.surface, shadowElevation = 2.dp) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                Button(onClick = { vm.openTemplate(false) }, enabled = !busy, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    FinanceIcon(FinanceSymbol.PLUS, Modifier.size(18.dp), colors.onPrimary); Spacer(Modifier.width(8.dp)); Text("Create Recurring Expense")
                }
            }
        } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxSize().testTag("recurring_sections"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message != null) item { AdvancedExpenseError(message, retry, busy, vm::retry, vm::clearMessage) }
                item { RecurringSummary(state, subscriptions) }
                if (!subscriptions) item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recurringFilters.forEach { filter -> FilterChip(selected = state.controls.recurringFilter == filter.key,
                            onClick = { vm.recurringFilter(filter.key) }, modifier = Modifier.heightIn(min = 48.dp).testTag("recurring_filter_${filter.key}"),
                            label = { Text("${filter.label} (${filter.count})", style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primary, selectedLabelColor = colors.onPrimary)) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (subscriptions) "Your subscriptions" else "Payment templates", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Text("${rows.size} total", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                if (rows.isEmpty()) item { FinancePanel {
                    ExpenseSectionTitle(if (subscriptions) "No subscriptions yet" else if (state.recurring.isNotEmpty()) "No matching schedules" else "No recurring expenses yet", ExpenseSymbol.REPEAT)
                    Text(if (!subscriptions && state.recurring.isNotEmpty()) "Choose another filter to see your payment templates." else "Create a template, then confirm each payment when it is due. Your ledger updates after confirmation.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    if (!subscriptions && state.recurring.isNotEmpty()) TextButton(onClick = { vm.recurringFilter("all") }) { Text("Clear filters") }
                    else Button(onClick = { vm.openTemplate(subscriptions) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(if (subscriptions) "Add Subscription" else "Add Recurring Expense")
                    }
                } }
                items(rows, key = { it.template.id }) { row -> if (subscriptions) RecurringManagementCard(row, busy, state.controls.menu, vm) else RecurringScheduleCard(row, busy, state.controls.menu, vm) }
                if (!subscriptions && state.dueRecurring == 0 && state.nextRecurringPayment != null) item {
                    Surface(shape = RoundedCornerShape(16.dp), color = colors.primaryContainer) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FinanceIcon(FinanceSymbol.SHIELD, Modifier.size(22.dp), colors.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("All caught up", style = MaterialTheme.typography.titleSmall)
                            Text(state.nextRecurringPayment, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    } }
                }
            }
        }
    }
    RecurringManagementDialogs(state, busy, message, vm)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringSummary(state: AdvancedExpenseScreenState, subscriptions: Boolean) {
    Surface(shape = RoundedCornerShape(20.dp), color = VaultGreen, modifier = Modifier.fillMaxWidth().testTag("recurring_summary")) {
        Column(Modifier.background(Brush.linearGradient(listOf(VaultDeep, VaultGreen, Color(0xFF07513D)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseIcon(if (subscriptions) ExpenseSymbol.CARD else ExpenseSymbol.REPEAT, Modifier.size(18.dp), VaultMint)
                Text(if (subscriptions) "MONTHLY SUBSCRIPTIONS" else "STAY ONE STEP AHEAD", style = MaterialTheme.typography.labelMedium, color = VaultMuted)
            }
            if (subscriptions) {
                FinanceValue(state.monthlyTotal, Modifier.testTag("subscription_monthly_total"), large = true, color = Color.White)
                Text("Monthly estimate from active subscriptions", style = MaterialTheme.typography.bodySmall, color = VaultMuted)
            } else {
                Text("Create a template once. Confirm each payment when it is due.", style = MaterialTheme.typography.bodySmall, color = VaultMuted)
                Text("TOTAL SCHEDULED / MONTH", style = MaterialTheme.typography.labelSmall, color = VaultMuted)
                FinanceValue(state.recurringMonthlyTotal, Modifier.testTag("recurring_monthly_total"), large = true, color = Color.White)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryPill("${if (subscriptions) state.activeSubscriptions else state.activeRecurring} active", VaultMint)
                SummaryPill(if (subscriptions) "${state.dueSubscriptions} due" else "${state.dueSoonRecurring} due soon", VaultMint)
                if (subscriptions && state.reviewSubscriptions > 0) SummaryPill("${state.reviewSubscriptions} to review", ExpenseOrange)
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = .12f)) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringManagementCard(row: RecurringRecordState, busy: Boolean, menu: String?, vm: AdvancedExpenseViewModel) {
    val colors = MaterialTheme.colorScheme
    val item = row.template
    Surface(Modifier.fillMaxWidth().testTag("template_${item.id}"), shape = RoundedCornerShape(18.dp), color = colors.surface,
        border = BorderStroke(1.dp, if (row.due || row.review) colors.primary.copy(alpha = .3f) else colors.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints {
                val stacked = maxWidth < 290.dp || LocalDensity.current.fontScale > 1.15f || row.amount.length > 22
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(Modifier.size(36.dp), color = categoryColor(item.categoryName).copy(alpha = .12f), shape = RoundedCornerShape(11.dp)) {
                        Box(contentAlignment = Alignment.Center) { ExpenseIcon(categorySymbol(item.categoryName), Modifier.size(19.dp), categoryColor(item.categoryName)) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        if (stacked) Text(row.frequency, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    if (!stacked) Column(Modifier.weight(1.05f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        FinanceValue(row.amount, Modifier.testTag("template_amount_${item.id}"), align = TextAlign.End, color = colors.primary)
                        Text(row.frequency, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Box {
                        IconButton(onClick = { vm.recordMenu(item.id) }, enabled = !busy, modifier = Modifier.size(32.dp)) {
                            ExpenseIcon(ExpenseSymbol.MORE, Modifier.size(18.dp).semantics { contentDescription = "Actions for ${item.title}" })
                        }
                        DropdownMenu(expanded = menu == item.id, onDismissRequest = { vm.recordMenu(null) }) {
                            DropdownMenuItem(text = { Text("Edit") }, onClick = { vm.openTemplate(item.subscription, item) })
                            if (item.subscription && item.active) DropdownMenuItem(text = { Text("Cancel Subscription") }, onClick = { vm.recordMenu(null); vm.cancelSubscription(item) })
                            DropdownMenuItem(text = { Text("Delete", color = colors.error) }, onClick = { vm.requestDelete(item) })
                        }
                    }
                }
                if (stacked) FinanceValue(row.amount, Modifier.fillMaxWidth().padding(start = 45.dp).testTag("template_amount_${item.id}"), color = colors.primary)
                }
            }
            Text(row.details, Modifier.padding(start = 45.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            row.reviewLabel?.let { Text(it, Modifier.padding(start = 45.dp), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (row.due) "Due · ${row.dueLabel}" else "Next · ${row.dueLabel}", style = MaterialTheme.typography.labelSmall,
                        color = if (row.due) colors.primary else colors.onSurfaceVariant)
                    if (row.status != "Active") Text(row.status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                TextButton(onClick = { vm.toggleActive(item) }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (item.active) "Pause" else "Resume") }
            }
            if (row.due || row.review) {
                HorizontalDivider(color = colors.outlineVariant)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (row.review) OutlinedButton(onClick = { vm.dismissReview(item) }, enabled = !busy, shape = RoundedCornerShape(10.dp)) { Text("Keep") }
                    if (row.due) Button(onClick = { vm.requestConfirmation(item) }, enabled = !busy, shape = RoundedCornerShape(10.dp)) { Text("Confirm Payment") }
                }
            }
        }
    }
}

@Composable
private fun RecurringManagementDialogs(state: AdvancedExpenseScreenState, busy: Boolean, message: String?, vm: AdvancedExpenseViewModel) {
    state.controls.deleteTarget?.let { item ->
        AlertDialog(onDismissRequest = { if (!busy) vm.requestDelete(null) }, title = { Text("Delete Template?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete '${item.title}'? Previously recorded expenses will stay in your ledger.")
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } }, confirmButton = { TextButton(onClick = vm::deleteSelectedTemplate, enabled = !busy) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { vm.requestDelete(null) }, enabled = !busy) { Text("Cancel") } })
    }
    state.controls.confirmTarget?.let { item ->
        AlertDialog(onDismissRequest = { if (!busy) vm.requestConfirmation(null) }, title = { Text("Confirm Payment") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Record '${item.title}' on its scheduled date (${item.nextDueDate}). Adjust the actual amount below.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(state.controls.confirmAmount, vm::confirmationAmount, label = { Text("Amount (LKR)") },
                    singleLine = true, enabled = !busy, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                (state.controls.confirmError ?: message)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { Button(onClick = vm::confirmSelectedTemplate, enabled = !busy) { Text(if (busy) "Saving…" else "Confirm & Record") } },
            dismissButton = { TextButton(onClick = { vm.requestConfirmation(null) }, enabled = !busy) { Text("Cancel") } })
    }
}
