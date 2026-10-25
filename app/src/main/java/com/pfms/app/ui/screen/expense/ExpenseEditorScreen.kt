package com.pfms.app.ui.screen.expense

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.*
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.ExpenseDraftState

data class ExpenseDraftActions(val back: () -> Unit, val save: () -> Unit, val amount: (String) -> Unit,
    val category: (String) -> Unit, val payment: (ExpensePaymentMethod) -> Unit,
    val classification: (ExpenseClassification?) -> Unit, val merchant: (String) -> Unit,
    val note: (String) -> Unit, val menu: (String?) -> Unit, val increment: (Long) -> Unit,
    val date: (Long?) -> Unit, val repeat: (String) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(state: ExpenseDraftState, actions: ExpenseDraftActions, onManageRecurring: (() -> Unit)? = null) = FinanceTheme {
    val colors = MaterialTheme.colorScheme
    BackHandler { if (!state.saving) actions.back() }
    Scaffold(Modifier.imePadding(), containerColor = colors.background,
        topBar = { TopAppBar(title = { Column {
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            Text("Record and categorize your spending", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        } }, navigationIcon = { IconButton(onClick = actions.back, enabled = !state.saving) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)) },
        bottomBar = { FinanceSaveBar(state.saveLabel, state.saving, state.error, actions.save, actions.back) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("expense_form_sections").padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.past.isNotEmpty() && state.title != "Edit Expense") OutlinedButton(onClick = { actions.menu("repeat") }, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(13.dp)) {
                    ExpenseIcon(ExpenseSymbol.REPEAT, tint = colors.primary); Spacer(Modifier.width(8.dp)); Text("Select & Repeat Past Expense")
                }
                ExpenseAmountPanel(state, actions)
                FinancePanel {
                    ExpenseSectionTitle("Basic Details", ExpenseSymbol.BAG)
                    ExpenseBasicFields(state, actions)
                }
                FinancePanel {
                    ExpenseSectionTitle("Additional Details", ExpenseSymbol.NOTE)
                    ExpenseInput(state.merchant, actions.merchant, "Merchant (Optional)", !state.saving)
                    ExpenseInput(state.note, actions.note, "Description / Note (optional)", !state.saving, multiline = true)
                    onManageRecurring?.let { callback -> TextButton(onClick = callback, enabled = !state.saving) { Text("Manage recurring expenses") } }
                }
            }
        }
    }
    ExpenseEditorDialogs(state, actions)
}

@Composable
internal fun ExpenseAmountPanel(state: ExpenseDraftState, actions: ExpenseDraftActions, autofocus: Boolean = false, showHeading: Boolean = true) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autofocus) { if (autofocus) { focus.requestFocus(); keyboard?.show() } }
    FinancePanel(Modifier.testTag("expense_form_amount_panel")) {
        if (showHeading) Text("EXPENSE AMOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(state.amount, actions.amount, Modifier.fillMaxWidth().focusRequester(focus), enabled = !state.saving,
            label = { Text("Amount (LKR)") }, prefix = { Text("LKR  ") }, singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall, shape = RoundedCornerShape(13.dp),
            colors = expenseFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
        state.formatted?.let { FinanceValue(it, Modifier.testTag("expense_form_value")) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(500L to "+500", 1000L to "+1k", 5000L to "+5k", 10000L to "+10k").forEach { (value, label) ->
                OutlinedButton(onClick = { actions.increment(value) }, enabled = !state.saving, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) { Text(label, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
internal fun ExpenseBasicFields(state: ExpenseDraftState, actions: ExpenseDraftActions, showCategory: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val category: @Composable (Modifier) -> Unit = { modifier -> Box(modifier) {
        ExpenseSelector("Category *", state.category?.name ?: "Select Category", ExpenseSymbol.BAG, { actions.menu("category") }, !state.saving)
        DropdownMenu(state.menu == "category", { actions.menu(null) }) {
            state.categories.forEach { option -> DropdownMenuItem(text = { Text(option.name) }, onClick = { actions.category(option.id) }) }
        }
    } }
    val payment: @Composable (Modifier) -> Unit = { modifier -> Box(modifier) {
        ExpenseSelector("Payment Method *", state.paymentLabel, ExpenseSymbol.CARD, { actions.menu("payment") }, !state.saving)
        DropdownMenu(state.menu == "payment", { actions.menu(null) }) {
            listOf(ExpensePaymentMethod.CASH to "Cash", ExpensePaymentMethod.CARD to "Card",
                ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT to "Bank transfer / Auto-debit", ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM to "Digital wallet / Platform").forEach { (method, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { actions.payment(method) })
            }
        }
    } }
    val date: @Composable (Modifier) -> Unit = { modifier -> Box(modifier) {
        ExpenseSelector("Date *", state.dateLabel, ExpenseSymbol.CALENDAR, { actions.menu("date") }, !state.saving)
    } }
    BoxWithConstraints {
        val stack = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.15f
        if (!showCategory) {
            if (stack) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { payment(Modifier.fillMaxWidth()); date(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { payment(Modifier.weight(1f)); date(Modifier.weight(1f)) }
        } else if (maxWidth < 330.dp || stack) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { category(Modifier.fillMaxWidth()); payment(Modifier.fillMaxWidth()) }
        else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { category(Modifier.weight(1f)); payment(Modifier.weight(1f)) }
    }
    if (showCategory) date(Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Spending type", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        if (state.overridden) TextButton(onClick = { actions.classification(null) }) { Text("Use category default", style = MaterialTheme.typography.labelSmall) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExpenseClassification.entries.forEach { option -> FilterChip(state.classification == option, { actions.classification(option) }, enabled = !state.saving,
            label = { Text(if (option == ExpenseClassification.COMMITTED) "Committed" else "Discretionary", style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.primary)) }
    }
}

@Composable
internal fun ExpenseSelector(label: String, value: String, symbol: ExpenseSymbol, onClick: () -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = label }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseIcon(symbol, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                FinanceIcon(FinanceSymbol.DOWN, Modifier.size(14.dp))
            }
        }
    }
}

@Composable
internal fun ExpenseInput(value: String, onValue: (String) -> Unit, label: String, enabled: Boolean, multiline: Boolean = false) {
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = !multiline,
        minLines = if (multiline) 2 else 1, maxLines = if (multiline) 5 else 1, enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(12.dp), colors = expenseFieldColors())
}

@Composable
internal fun expenseFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseEditorDialogs(state: ExpenseDraftState, actions: ExpenseDraftActions) {
    if (state.menu == "date") {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.dateMillis)
        DatePickerDialog(onDismissRequest = { actions.menu(null) }, confirmButton = {
            TextButton(onClick = { actions.date(picker.selectedDateMillis) }, enabled = picker.selectedDateMillis != null) { Text("Select") }
        }, dismissButton = { TextButton(onClick = { actions.menu(null) }) { Text("Cancel") } }) { DatePicker(picker) }
    }
    if (state.menu == "repeat") AlertDialog(onDismissRequest = { actions.menu(null) }, title = { Text("Select Previous Expense") },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.past, key = { it.id }) { row -> Surface(onClick = { actions.repeat(row.id) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(row.title, style = MaterialTheme.typography.titleSmall); Text(row.subtitle, style = MaterialTheme.typography.bodySmall) }
            } }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = { actions.menu(null) }) { Text("Cancel") } })
}
