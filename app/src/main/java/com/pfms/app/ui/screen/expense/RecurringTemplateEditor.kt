package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.*
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*

@Composable
internal fun RecurringTemplateEditor(state: AdvancedExpenseScreenState, busy: Boolean, message: String?, retry: Boolean, vm: AdvancedExpenseViewModel) {
    val draft = state.controls.editor ?: return
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(Modifier.imePadding(), containerColor = colors.background,
        topBar = { AdvancedExpenseHeader(if (draft.editing) "Edit Template" else if (draft.original.subscription) "New Subscription" else "New Recurring Expense",
            "Set the amount and payment schedule", vm::closeTemplate) },
        bottomBar = { FinanceSaveBar(draft.saveLabel, busy, draft.error ?: message, { keyboard?.hide(); vm.saveTemplateDraft() }, vm::closeTemplate) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("template_form_sections").padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AdvancedExpenseError(message, retry, busy, vm::retry, vm::clearMessage)
                FinancePanel {
                    ExpenseSectionTitle("Template details", ExpenseSymbol.NOTE)
                    ExpenseInput(draft.title, vm::templateTitle, "Template Name / Merchant", !busy)
                    Box {
                        ExpenseSelector("Category", state.selectedCategory?.name ?: "Select Category", ExpenseSymbol.GRID, { vm.templateMenu("category") }, !busy)
                        DropdownMenu(draft.menu == "category", { vm.templateMenu(null) }) {
                            state.activeCategories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { vm.templateCategory(category.id) }) }
                        }
                    }
                    Box {
                        ExpenseSelector("Payment Method", draft.paymentLabel, ExpenseSymbol.CARD, { vm.templateMenu("payment") }, !busy)
                        DropdownMenu(draft.menu == "payment", { vm.templateMenu(null) }) {
                            listOf(ExpensePaymentMethod.CASH to "Cash", ExpensePaymentMethod.CARD to "Card",
                                ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT to "Bank transfer / Auto-debit",
                                ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM to "Digital wallet / Platform").forEach { (value, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { vm.templatePayment(value) })
                            }
                        }
                    }
                }
                FinancePanel {
                    ExpenseSectionTitle("Amount & schedule", ExpenseSymbol.REPEAT)
                    OutlinedTextField(draft.amount, vm::templateAmount, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Amount in LKR (e.g. 1500.00)", style = MaterialTheme.typography.bodySmall) },
                        prefix = { Text("LKR ", color = colors.primary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, enabled = !busy, shape = RoundedCornerShape(12.dp), colors = expenseFieldColors(),
                        textStyle = MaterialTheme.typography.titleLarge)
                    Text("Payment frequency", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Recurrence.DAILY to "Daily", Recurrence.WEEKLY to "Weekly", Recurrence.MONTHLY to "Monthly").forEach { (value, label) ->
                            FilterChip(selected = draft.recurrence == value, onClick = { vm.templateRecurrence(value) }, enabled = !busy,
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.primary))
                        }
                    }
                    ExpenseSelector("Next Due Date", draft.dueLabel, ExpenseSymbol.CALENDAR, { vm.templateMenu("dueDate") }, !busy)
                    Text("Payments are recorded after you confirm them. The next date follows this schedule.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (draft.original.subscription) FinancePanel {
                    ExpenseSectionTitle("Review & reminders", ExpenseSymbol.CALENDAR)
                    ExpenseSelector("Review Reminder Date (optional)", draft.reminderLabel, ExpenseSymbol.CALENDAR, { vm.templateMenu("reminderDate") }, !busy)
                    if (draft.reminder.isNotBlank()) TextButton(onClick = vm::clearTemplateReminder, enabled = !busy) { Text("Clear reminder") }
                    Text("Review flag", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    BoxWithConstraints {
                        val options = listOf(SubscriptionFlag.NONE to "None", SubscriptionFlag.REVIEW to "Review", SubscriptionFlag.CANCEL to "Cancel")
                        if (maxWidth < 270.dp && LocalDensity.current.fontScale > 1.15f) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            options.forEach { (value, label) -> ReviewFlagChip(value, label, draft, busy, Modifier.fillMaxWidth(), vm) }
                        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            options.forEach { (value, label) -> ReviewFlagChip(value, label, draft, busy, Modifier.weight(1f), vm) }
                        }
                    }
                    Text("Flag renewals for review. Cancelled subscriptions are excluded from the monthly estimate.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
    TemplateDateDialog(draft, vm)
}

@Composable
private fun ReviewFlagChip(value: SubscriptionFlag, label: String, draft: RecurringEditorState, busy: Boolean, modifier: Modifier, vm: AdvancedExpenseViewModel) {
    FilterChip(selected = draft.flag == value, onClick = { vm.templateFlag(value) }, enabled = !busy,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) }, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.primary))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateDateDialog(draft: RecurringEditorState, vm: AdvancedExpenseViewModel) {
    if (draft.menu != "dueDate" && draft.menu != "reminderDate") return
    val reminder = draft.menu == "reminderDate"
    val picker = rememberDatePickerState(initialSelectedDateMillis = if (reminder) draft.reminderMillis ?: draft.dueMillis else draft.dueMillis)
    DatePickerDialog(onDismissRequest = { vm.templateMenu(null) }, confirmButton = {
        TextButton(onClick = { vm.templateDate(picker.selectedDateMillis, reminder) }, enabled = picker.selectedDateMillis != null) { Text("Select") }
    }, dismissButton = { TextButton(onClick = { vm.templateMenu(null) }) { Text("Cancel") } }) { DatePicker(picker) }
}
