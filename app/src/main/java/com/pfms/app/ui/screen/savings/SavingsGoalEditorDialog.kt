package com.pfms.app.ui.screen.savings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.ui.component.*
import com.pfms.app.viewmodel.*

@Composable
internal fun SavingsGoalEditorDialog(state: SavingsScreenState, vm: SavingsGoalScreenViewModel, onSave: () -> Unit) {
    val draft = state.goalDraft ?: return
    val busy = state.source.operationInProgress
    val keyboard = LocalSoftwareKeyboardController.current
    SavingsFormDialog(if (draft.original == null) "Create Savings Goal" else "Edit Savings Goal", "Define your target & milestone.",
        busy, state.source.errorMessage, if (state.source.canRetry && state.source.errorMessage != null) "Retry" else if (draft.original == null) "Create Goal" else "Save Changes",
        vm::dismissGoal, { keyboard?.hide(); onSave() }) {
        SavingsInput(draft.name, vm::goalName, "Goal Name", !busy, error = draft.errors?.nameError)
        SavingsInput(draft.target, vm::goalTarget, "Target Amount (LKR)", !busy, money = true, error = draft.errors?.targetAmountError)
        SavingsDateField("Target Date (YYYY-MM-DD)", draft.date, !busy, draft.errors?.targetDateError) { keyboard?.hide(); vm.goalPicker(true) }
        SavingsInput(draft.opening, vm::goalOpening, "Opening Saved Amount (LKR)", !busy, money = true, error = draft.errors?.openingSavedError)
        Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FinanceIcon(FinanceSymbol.CHART, Modifier.size(23.dp), MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Monthly target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    draft.monthlyPreview?.let { FinanceValue(it, Modifier.testTag("goal_monthly_preview"), color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Text(draft.monthsLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (draft.picker) SavingsDatePicker(draft.dateMillis, { vm.goalPicker(false) }, vm::goalDate)
}

@Composable
internal fun SavingsTransactionEditorDialog(state: SavingsScreenState, vm: SavingsGoalScreenViewModel, onSave: () -> Unit) {
    val draft = state.transactionDraft ?: return
    val busy = state.source.operationInProgress
    val contribution = draft.type == GoalTransactionType.CONTRIBUTION
    val keyboard = LocalSoftwareKeyboardController.current
    SavingsFormDialog(if (draft.original != null) "Edit Transaction" else if (contribution) "Add Contribution" else "Record Withdrawal",
        "Keep your goal ledger up to date.", busy, state.source.errorMessage,
        if (state.source.canRetry && state.source.errorMessage != null) "Retry" else if (draft.original != null) "Save Changes" else if (contribution) "Confirm Deposit" else "Confirm Withdrawal",
        vm::dismissTransaction, { keyboard?.hide(); onSave() }) {
        if (!contribution) Text("Available goal savings: ${state.display.saved}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SavingsInput(draft.amount, vm::transactionAmount, "Amount (LKR)", !busy, money = true, error = draft.errors?.amountError)
        SavingsDateField("Date (YYYY-MM-DD)", draft.date, !busy, draft.errors?.dateError) { keyboard?.hide(); vm.transactionPicker(true) }
        SavingsInput(draft.note, vm::transactionNote, "Note (Optional)", !busy, multiline = true)
    }
    if (draft.picker) SavingsDatePicker(draft.dateMillis, { vm.transactionPicker(false) }, vm::transactionDate)
}

@Composable
private fun SavingsFormDialog(title: String, subtitle: String, busy: Boolean, error: String?, label: String,
    dismiss: () -> Unit, save: () -> Unit, fields: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0.28f) }
        Box(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, end = 6.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.size(36.dp), shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Box(contentAlignment = Alignment.Center) { FinanceIcon(FinanceSymbol.GOAL, Modifier.size(22.dp)) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.titleLarge)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = dismiss, enabled = !busy) { FinanceIcon(FinanceSymbol.CLOSE, Modifier.size(20.dp), description = "Close form") }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("savings_form_fields").padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp), content = fields)
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = dismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
                            Button(onClick = save, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                else Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingsInput(value: String, change: (String) -> Unit, label: String, enabled: Boolean, money: Boolean = false, error: String? = null, multiline: Boolean = false) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = !multiline, minLines = if (multiline) 2 else 1, maxLines = if (multiline) 4 else 1, enabled = enabled,
        prefix = if (money) { { Text("LKR ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } } else null,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (money) KeyboardType.Decimal else KeyboardType.Text),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun SavingsDateField(label: String, value: String, enabled: Boolean, error: String?, pick: () -> Unit) {
    OutlinedTextField(value, {}, modifier = Modifier.fillMaxWidth(), label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        readOnly = true, singleLine = true, enabled = enabled, textStyle = MaterialTheme.typography.bodyLarge, shape = RoundedCornerShape(12.dp),
        trailingIcon = { IconButton(onClick = pick, enabled = enabled) { FinanceIcon(FinanceSymbol.CALENDAR, Modifier.size(20.dp), description = "Choose $label") } },
        isError = error != null, supportingText = error?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsDatePicker(initial: Long?, dismiss: () -> Unit, select: (Long?) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = dismiss, confirmButton = {
        TextButton(onClick = { select(picker.selectedDateMillis) }, enabled = picker.selectedDateMillis != null) { Text("Select") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } }) { DatePicker(picker) }
}

@Composable
internal fun SavingsGoalOverlays(state: SavingsScreenState, vm: SavingsGoalScreenViewModel, archive: (String) -> Unit, delete: (String) -> Unit, deleteTransaction: (String) -> Unit) {
    if (state.help) AlertDialog(onDismissRequest = { vm.help(false) }, title = { Text("Your savings goal") },
        text = { Text("Record contributions and withdrawals to track the money saved for your target. The monthly plan uses your remaining target and calendar months. Income and expenses inform your pace without automatically funding the goal.") },
        confirmButton = { TextButton(onClick = { vm.help(false) }) { Text("Got it") } })
    state.confirmation?.let { kind ->
        val goal = state.source.activeGoal
        AlertDialog(onDismissRequest = { if (!state.source.operationInProgress) vm.confirmation(null) },
            title = { Text(when(kind) { "archive" -> "Archive Savings Goal"; "transaction" -> "Delete Transaction"; else -> "Delete Savings Goal" }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when(kind) { "archive" -> "Archive '${goal?.name.orEmpty()}' and make room for your next goal?"; "transaction" -> "Remove this entry from the savings ledger? Your saved balance will be recalculated."; else -> "Delete '${goal?.name.orEmpty()}' and its savings ledger? This cannot be undone." })
                state.source.errorMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            } }, confirmButton = { TextButton(onClick = {
                when (kind) { "archive" -> goal?.let { archive(it.id) }; "transaction" -> state.transactionToDelete?.let(deleteTransaction); else -> goal?.let { delete(it.id) } }
            }, enabled = !state.source.operationInProgress) { Text(if (kind == "archive") "Archive" else "Delete", color = if (kind == "archive") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { vm.confirmation(null) }, enabled = !state.source.operationInProgress) { Text("Cancel") } })
    }
}
