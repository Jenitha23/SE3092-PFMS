package com.pfms.app.ui.screen.savings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.GoalTransaction
import com.pfms.app.domain.model.GoalTransactionType
import com.pfms.app.domain.model.SavingsGoal
import com.pfms.app.domain.model.SavingsGoalDefaults
import com.pfms.app.ui.theme.DarkNavyText
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.ui.theme.ErrorExpense
import com.pfms.app.ui.theme.SecondaryText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Dialog for creating a new Savings Goal with editable pre-filled defaults (FR-23).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, targetAmountMinor: Long, targetDate: String, openingSavedMinor: Long) -> Unit,
    operationInProgress: Boolean
) {
    val defaultGoal = remember { SavingsGoalDefaults.createPrefilledExample() }

    var name by rememberSaveable { mutableStateOf(defaultGoal.name) }
    var targetAmountStr by rememberSaveable {
        mutableStateOf(SavingsGoalUiFormatter.formatMinorToDecimalString(defaultGoal.targetAmountMinor))
    }
    var targetDate by rememberSaveable { mutableStateOf(defaultGoal.targetDate) }
    var openingSavedStr by rememberSaveable {
        mutableStateOf(SavingsGoalUiFormatter.formatMinorToDecimalString(defaultGoal.openingSavedAmountMinor))
    }

    var validationResult by remember {
        mutableStateOf<SavingsGoalUiFormatter.GoalFormValidationResult?>(null)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = {
            Text(
                text = "Create Savings Goal",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkNavyText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationResult = null
                    },
                    label = { Text("Goal Name") },
                    isError = validationResult?.nameError != null,
                    supportingText = validationResult?.nameError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetAmountStr,
                    onValueChange = {
                        targetAmountStr = it
                        validationResult = null
                    },
                    label = { Text("Target Amount (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationResult?.targetAmountError != null,
                    supportingText = validationResult?.targetAmountError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Date (YYYY-MM-DD)") },
                    trailingIcon = {
                        Text(
                            text = "📅",
                            modifier = Modifier
                                .clickable { showDatePicker = true }
                                .padding(8.dp)
                        )
                    },
                    isError = validationResult?.targetDateError != null,
                    supportingText = validationResult?.targetDateError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = openingSavedStr,
                    onValueChange = {
                        openingSavedStr = it
                        validationResult = null
                    },
                    label = { Text("Opening Saved Amount (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationResult?.openingSavedError != null,
                    supportingText = validationResult?.openingSavedError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = SavingsGoalUiFormatter.validateGoalForm(
                        name = name,
                        targetAmountInput = targetAmountStr,
                        targetDateInput = targetDate,
                        openingSavedInput = openingSavedStr
                    )
                    if (result.isValid) {
                        onConfirm(
                            name.trim(),
                            result.targetAmountMinor,
                            targetDate.trim(),
                            result.openingSavedAmountMinor
                        )
                    } else {
                        validationResult = result
                    }
                },
                enabled = !operationInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Create Goal", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !operationInProgress
            ) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(targetDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull() ?: System.currentTimeMillis()

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        targetDate = selectedDate.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("Select", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = EmeraldPrimary,
                    todayDateBorderColor = EmeraldPrimary,
                    todayContentColor = EmeraldPrimary
                )
            )
        }
    }
}

/**
 * Dialog for editing an existing savings goal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGoalDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onConfirm: (SavingsGoal) -> Unit,
    operationInProgress: Boolean
) {
    var name by rememberSaveable(goal.id) { mutableStateOf(goal.name) }
    var targetAmountStr by rememberSaveable(goal.id) {
        mutableStateOf(SavingsGoalUiFormatter.formatMinorToDecimalString(goal.targetAmountMinor))
    }
    var targetDate by rememberSaveable(goal.id) { mutableStateOf(goal.targetDate) }
    var openingSavedStr by rememberSaveable(goal.id) {
        mutableStateOf(SavingsGoalUiFormatter.formatMinorToDecimalString(goal.openingSavedAmountMinor))
    }

    var validationResult by remember {
        mutableStateOf<SavingsGoalUiFormatter.GoalFormValidationResult?>(null)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = {
            Text(
                text = "Edit Savings Goal",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkNavyText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationResult = null
                    },
                    label = { Text("Goal Name") },
                    isError = validationResult?.nameError != null,
                    supportingText = validationResult?.nameError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetAmountStr,
                    onValueChange = {
                        targetAmountStr = it
                        validationResult = null
                    },
                    label = { Text("Target Amount (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationResult?.targetAmountError != null,
                    supportingText = validationResult?.targetAmountError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Target Date (YYYY-MM-DD)") },
                    trailingIcon = {
                        Text(
                            text = "📅",
                            modifier = Modifier
                                .clickable { showDatePicker = true }
                                .padding(8.dp)
                        )
                    },
                    isError = validationResult?.targetDateError != null,
                    supportingText = validationResult?.targetDateError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = openingSavedStr,
                    onValueChange = {
                        openingSavedStr = it
                        validationResult = null
                    },
                    label = { Text("Opening Saved Amount (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationResult?.openingSavedError != null,
                    supportingText = validationResult?.openingSavedError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = SavingsGoalUiFormatter.validateGoalForm(
                        name = name,
                        targetAmountInput = targetAmountStr,
                        targetDateInput = targetDate,
                        openingSavedInput = openingSavedStr
                    )
                    if (result.isValid) {
                        onConfirm(
                            goal.copy(
                                name = name.trim(),
                                targetAmountMinor = result.targetAmountMinor,
                                targetDate = targetDate.trim(),
                                openingSavedAmountMinor = result.openingSavedAmountMinor,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        validationResult = result
                    }
                },
                enabled = !operationInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save Changes", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !operationInProgress
            ) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(targetDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull() ?: System.currentTimeMillis()

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        targetDate = selectedDate.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("Select", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = EmeraldPrimary,
                    todayDateBorderColor = EmeraldPrimary,
                    todayContentColor = EmeraldPrimary
                )
            )
        }
    }
}

/**
 * Dialog for adding a Contribution or Withdrawal transaction (FR-24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    transactionType: GoalTransactionType,
    onDismiss: () -> Unit,
    onConfirm: (amountMinor: Long, note: String?, date: String) -> Unit,
    operationInProgress: Boolean
) {
    val isContribution = transactionType == GoalTransactionType.CONTRIBUTION
    val title = if (isContribution) "Add Contribution" else "Record Withdrawal"

    var amountStr by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    var validationResult by remember {
        mutableStateOf<SavingsGoalUiFormatter.TransactionFormValidationResult?>(null)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkNavyText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        validationResult = null
                    },
                    label = { Text("Amount (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationResult?.amountError != null,
                    supportingText = validationResult?.amountError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date (YYYY-MM-DD)") },
                    trailingIcon = {
                        Text(
                            text = "📅",
                            modifier = Modifier
                                .clickable { showDatePicker = true }
                                .padding(8.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = SavingsGoalUiFormatter.validateTransactionForm(
                        amountInput = amountStr,
                        dateInput = date
                    )
                    if (result.isValid) {
                        onConfirm(
                            result.amountMinor,
                            note.trim().ifBlank { null },
                            date.trim()
                        )
                    } else {
                        validationResult = result
                    }
                },
                enabled = !operationInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isContribution) EmeraldPrimary else DarkNavyText
                )
            ) {
                Text(
                    text = if (isContribution) "Confirm Deposit" else "Confirm Withdrawal",
                    color = Color.White
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !operationInProgress
            ) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull() ?: System.currentTimeMillis()

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        date = selectedDate.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("Select", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = EmeraldPrimary,
                    todayDateBorderColor = EmeraldPrimary,
                    todayContentColor = EmeraldPrimary
                )
            )
        }
    }
}

/**
 * Dialog for editing an existing transaction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: GoalTransaction,
    onDismiss: () -> Unit,
    onConfirm: (GoalTransaction) -> Unit,
    operationInProgress: Boolean
) {
    var amountStr by rememberSaveable(transaction.id) {
        mutableStateOf(SavingsGoalUiFormatter.formatMinorToDecimalString(transaction.amountMinor))
    }
    var note by rememberSaveable(transaction.id) { mutableStateOf(transaction.note ?: "") }
    var date by rememberSaveable(transaction.id) { mutableStateOf(transaction.date) }

    var validationResult by remember {
        mutableStateOf<SavingsGoalUiFormatter.TransactionFormValidationResult?>(null)
    }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = {
            Text(
                text = "Edit Transaction",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = DarkNavyText
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = {
                        amountStr = it
                        validationResult = null
                    },
                    label = { Text("Amount (LKR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = validationResult?.amountError != null,
                    supportingText = validationResult?.amountError?.let { { Text(it, color = ErrorExpense) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date (YYYY-MM-DD)") },
                    trailingIcon = {
                        Text(
                            text = "📅",
                            modifier = Modifier
                                .clickable { showDatePicker = true }
                                .padding(8.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = SavingsGoalUiFormatter.validateTransactionForm(
                        amountInput = amountStr,
                        dateInput = date
                    )
                    if (result.isValid) {
                        onConfirm(
                            transaction.copy(
                                amountMinor = result.amountMinor,
                                date = date.trim(),
                                note = note.trim().ifBlank { null },
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        validationResult = result
                    }
                },
                enabled = !operationInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Save Changes", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !operationInProgress
            ) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull() ?: System.currentTimeMillis()

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        date = selectedDate.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("Select", color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = SecondaryText)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = EmeraldPrimary,
                    todayDateBorderColor = EmeraldPrimary,
                    todayContentColor = EmeraldPrimary
                )
            )
        }
    }
}

/**
 * Reusable confirmation dialog for destructive / lifecycle actions (Delete Goal, Archive Goal, Delete Transaction).
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmButtonText: String = "Confirm",
    isDestructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    operationInProgress: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!operationInProgress) onDismiss() },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDestructive) ErrorExpense else DarkNavyText
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = DarkNavyText
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !operationInProgress,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) ErrorExpense else EmeraldPrimary
                )
            ) {
                Text(confirmButtonText, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !operationInProgress
            ) {
                Text("Cancel", color = SecondaryText)
            }
        }
    )
}
