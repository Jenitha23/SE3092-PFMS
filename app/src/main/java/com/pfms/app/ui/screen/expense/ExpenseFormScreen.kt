package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.*
import com.pfms.app.viewmodel.ExpenseFormData
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(
    initialExpense: Expense?, categories: List<ExpenseCategoryOption>, isSaving: Boolean,
    onSave: (ExpenseFormData) -> Unit, onBack: () -> Unit, errorMessage: String? = null,
    defaultPaymentMethod: ExpensePaymentMethod = ExpensePaymentMethod.CASH,
    isRepeating: Boolean = false, pastExpenses: List<Expense> = emptyList(),
    onManageRecurring: (() -> Unit)? = null
) = ExpenseUiTheme {
    val editing = initialExpense != null && !isRepeating
    val initialId = initialExpense?.id
    var showRepeatPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var amount by rememberSaveable(initialId) { mutableStateOf(initialExpense?.let { BigDecimal.valueOf(it.amountMinor, 2).toPlainString() } ?: "") }
    var selectedCategoryId by rememberSaveable(initialId) { mutableStateOf(initialExpense?.categoryId ?: "") }
    var date by rememberSaveable(initialId) { mutableStateOf(if (isRepeating) LocalDate.now(ZoneId.of("Asia/Colombo")).toString() else initialExpense?.date ?: LocalDate.now(ZoneId.of("Asia/Colombo")).toString()) }
    var merchant by rememberSaveable(initialId) { mutableStateOf(initialExpense?.merchant ?: "") }
    var note by rememberSaveable(initialId) { mutableStateOf(initialExpense?.note ?: "") }
    var paymentMethod by rememberSaveable(initialId) { mutableStateOf(initialExpense?.paymentMethod ?: defaultPaymentMethod) }
    var classificationOverride by rememberSaveable(initialId) { mutableStateOf(initialExpense?.classification) }
    var categoryMenu by remember { mutableStateOf(false) }
    var paymentMenu by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }
    val classification = classificationOverride ?: selectedCategory?.defaultClassification ?: ExpenseClassification.DISCRETIONARY
    val fieldColors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
    val screenTitle = if (editing) "Edit Expense" else if (isRepeating) "Repeat Expense" else "Add Expense"

    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { ExpenseHeader(screenTitle, "Make every expense count.", onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!editing && pastExpenses.isNotEmpty()) {
                OutlinedButton(onClick = { showRepeatPicker = true }, Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    ExpenseIcon(ExpenseSymbol.REPEAT, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp)); Text("Select & Repeat Past Expense")
                }
            }
            ExpensePanel {
                ExpenseSectionTitle("Basic Details", ExpenseSymbol.BAG)
                OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (LKR)") },
                    prefix = { Text("LKR  ", fontWeight = FontWeight.SemiBold) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), colors = fieldColors)
                BoxWithConstraints {
                    val stack = maxWidth < 310.dp
                    val categoryField: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier) {
                            ExpenseSelectField("Category *", selectedCategory?.name ?: "Select Category", categorySymbol(selectedCategory?.name.orEmpty()),
                                onClick = { categoryMenu = true })
                            DropdownMenu(categoryMenu, { categoryMenu = false }) {
                                categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) },
                                    leadingIcon = { ExpenseIcon(categorySymbol(category.name), tint = categoryColor(category.name)) },
                                    onClick = { selectedCategoryId = category.id; classificationOverride = null; categoryMenu = false }) }
                            }
                        }
                    }
                    val paymentField: @Composable (Modifier) -> Unit = { modifier ->
                        Box(modifier) {
                            ExpenseSelectField("Payment Method *", paymentLabel(paymentMethod), ExpenseSymbol.CARD, onClick = { paymentMenu = true })
                            DropdownMenu(paymentMenu, { paymentMenu = false }) {
                                ExpensePaymentMethod.entries.forEach { method -> DropdownMenuItem(text = { Text(paymentLabel(method)) }, onClick = { paymentMethod = method; paymentMenu = false }) }
                            }
                        }
                    }
                    if (stack) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { categoryField(Modifier.fillMaxWidth()); paymentField(Modifier.fillMaxWidth()) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { categoryField(Modifier.weight(1f)); paymentField(Modifier.weight(1f)) }
                }
                ExpenseSelectField("Date *", runCatching { LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) }.getOrDefault(date),
                    ExpenseSymbol.CALENDAR, onClick = { showDatePicker = true })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Spending type", style = MaterialTheme.typography.labelLarge)
                    if (classificationOverride != null) TextButton(onClick = { classificationOverride = null }, contentPadding = PaddingValues(0.dp)) { Text("Use category default", style = MaterialTheme.typography.labelSmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseClassification.entries.forEach { type ->
                        FilterChip(selected = classification == type, onClick = { classificationOverride = type },
                            label = { Text(if (type == ExpenseClassification.COMMITTED) "Committed" else "Discretionary") },
                            leadingIcon = if (classification == type) ({ ExpenseIcon(ExpenseSymbol.CHECK, Modifier.size(15.dp), MaterialTheme.colorScheme.primary) }) else null,
                            shape = RoundedCornerShape(10.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                    }
                }
            }
            ExpensePanel {
                ExpenseSectionTitle("Additional Details", ExpenseSymbol.NOTE)
                OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), label = { Text("Merchant (Optional)") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors)
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Description / Note (optional)") },
                    minLines = 3, shape = RoundedCornerShape(12.dp), colors = fieldColors)
                if (onManageRecurring != null) {
                    OutlinedButton(onClick = onManageRecurring, Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        ExpenseIcon(ExpenseSymbol.REPEAT, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp)); Text("Manage recurring expenses")
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExpenseIcon(ExpenseSymbol.INFO, tint = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Quick Tips", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text("Use categories to see where your money goes. You can edit or delete an expense later.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            errorMessage?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Button(enabled = !isSaving && selectedCategory != null && amount.isNotBlank(), onClick = {
                val category = selectedCategory ?: return@Button
                onSave(ExpenseFormData(amount, category.id, category.name, category.defaultClassification,
                    classificationOverride, paymentMethod, date, merchant, note))
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp)) {
                if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (editing) "Update Expense" else "Save Expense", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(enabled = !isSaving, onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text("Cancel", fontWeight = FontWeight.SemiBold) }
        }
    }
    if (showRepeatPicker) AlertDialog(onDismissRequest = { showRepeatPicker = false }, title = { Text("Select Previous Expense") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pastExpenses, key = { it.id }) { expense ->
                    Surface(onClick = {
                        amount = BigDecimal.valueOf(expense.amountMinor, 2).toPlainString(); selectedCategoryId = expense.categoryId
                        paymentMethod = expense.paymentMethod; classificationOverride = expense.classification
                        merchant = expense.merchant; note = expense.note; date = LocalDate.now(ZoneId.of("Asia/Colombo")).toString(); showRepeatPicker = false
                    }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryAvatar(expense.categoryName)
                            Column {
                                Text(expense.merchant.ifBlank { expense.categoryName }, fontWeight = FontWeight.SemiBold)
                                Text("LKR ${expenseMoney(expense.amountMinor)} · ${expense.date}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { showRepeatPicker = false }) { Text("Cancel") } })
    if (showDatePicker) {
        val initialDate = runCatching { LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                showDatePicker = false
            }) { Text("Select") }
        }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(picker) }
    }
}

@Composable
private fun ExpenseSelectField(label: String, value: String, symbol: ExpenseSymbol, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = label },
            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            ExpenseIcon(symbol, Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(value, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ExpenseIcon(ExpenseSymbol.CHEVRON, Modifier.size(15.dp))
        }
    }
}

private fun paymentLabel(method: ExpensePaymentMethod) = when (method) {
    ExpensePaymentMethod.CASH -> "Cash"
    ExpensePaymentMethod.CARD -> "Card"
    ExpensePaymentMethod.BANK_TRANSFER_AUTO_DEBIT -> "Bank transfer"
    ExpensePaymentMethod.DIGITAL_WALLET_PLATFORM -> "Digital wallet"
}
