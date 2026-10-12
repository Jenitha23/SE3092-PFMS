
package com.pfms.app.ui.screen.income

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.Income
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

// Data returned by the Add/Edit Income form
data class IncomeFormInput(
    val amount: String,
    val source: String,
    val currency: String,
    val receivedDate: String,
    val note: String
)

// Supported income sources
private val incomeSources = listOf(
    "Salary",
    "Freelance",
    "Google AdSense",
    "Crypto",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeFormScreen(
    onBack: () -> Unit,
    onSave: (IncomeFormInput) -> Unit,
    modifier: Modifier = Modifier,
    initialIncome: Income? = null,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    val context = LocalContext.current

    // Determine Add or Edit mode
    val isEditMode = initialIncome != null

    // 1. Income amount
    var amount by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.amountMinor?.let {
                BigDecimal.valueOf(it, 2).toPlainString()
            } ?: ""
        )
    }

    // 2. Income source
    var source by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.source ?: "Salary"
        )
    }

    // 3. Currency
    var currency by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.currency ?: "LKR"
        )
    }

    // 4. Received date
    var receivedDate by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.receivedDate
                ?: LocalDate.now(
                    ZoneId.of("Asia/Colombo")
                ).toString()
        )
    }

    // 5. Optional note
    var note by rememberSaveable(initialIncome?.id) {
        mutableStateOf(
            initialIncome?.note ?: ""
        )
    }

    // Dropdown state
    var sourceExpanded by remember {
        mutableStateOf(false)
    }

    // Validation error
    var amountError by remember {
        mutableStateOf<String?>(null)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) {
                            "Edit Income"
                        } else {
                            "Add Income"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Screen heading
            Text(
                text = if (isEditMode) {
                    "Update Income Details"
                } else {
                    "Record New Income"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (isEditMode) {
                    "Modify your existing income record."
                } else {
                    "Keep track of your earnings " +
                            "from different income sources."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // -------------------------------------
            // 1. INCOME AMOUNT
            // -------------------------------------
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it
                    amountError = null
                },
                label = {
                    Text("Income Amount")
                },
                placeholder = {
                    Text("0.00")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                isError = amountError != null,
                supportingText = {
                    amountError?.let {
                        Text(it)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // -------------------------------------
            // 2. INCOME SOURCE
            // -------------------------------------
            ExposedDropdownMenuBox(
                expanded = sourceExpanded,
                onExpandedChange = {
                    sourceExpanded = it
                }
            ) {
                OutlinedTextField(
                    value = source,
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text("Income Source")
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = sourceExpanded
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = sourceExpanded,
                    onDismissRequest = {
                        sourceExpanded = false
                    }
                ) {
                    incomeSources.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(item)
                            },
                            onClick = {
                                source = item
                                sourceExpanded = false
                            }
                        )
                    }
                }
            }

            // -------------------------------------
            // 3. CURRENCY
            // -------------------------------------
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Currency",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    listOf("LKR", "USD").forEach { item ->
                        FilterChip(
                            selected = currency == item,
                            onClick = {
                                currency = item
                            },
                            label = {
                                Text(item)
                            }
                        )
                    }
                }
            }

            // Currency conversion notice
            if (currency == "USD") {
                Text(
                    text = "USD to LKR conversion " +
                            "will be handled by the " +
                            "currency conversion module.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // -------------------------------------
            // 4. RECEIVED DATE
            // -------------------------------------
            OutlinedTextField(
                value = receivedDate,
                onValueChange = {},
                label = {
                    Text("Date Received")
                },
                readOnly = true,
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val selectedDate =
                                runCatching {
                                    LocalDate.parse(receivedDate)
                                }.getOrElse {
                                    LocalDate.now(
                                        ZoneId.of("Asia/Colombo")
                                    )
                                }

                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    receivedDate =
                                        LocalDate.of(
                                            year,
                                            month + 1,
                                            day
                                        ).toString()
                                },
                                selectedDate.year,
                                selectedDate.monthValue - 1,
                                selectedDate.dayOfMonth
                            ).show()
                        }
                    ) {
                        Text("Select")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // -------------------------------------
            // 5. OPTIONAL NOTES
            // -------------------------------------
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                },
                label = {
                    Text("Notes (Optional)")
                },
                placeholder = {
                    Text("Add additional details...")
                },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            // -------------------------------------
            // 6. ERROR MESSAGE
            // -------------------------------------
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // -------------------------------------
            // 7. SAVE / UPDATE BUTTON
            // -------------------------------------
            Button(
                onClick = {
                    val value = amount.trim()

                    val validAmount =
                        value.matches(
                            Regex("""\d+(\.\d{1,2})?""")
                        ) &&
                                value.toBigDecimalOrNull()?.let {
                                    it > BigDecimal.ZERO &&
                                            it <= BigDecimal(
                                        "92233720368547758.07"
                                    )
                                } == true

                    if (!validAmount) {
                        amountError =
                            "Enter a valid positive amount " +
                                    "with up to 2 decimal places."
                    } else {
                        onSave(
                            IncomeFormInput(
                                amount = value,
                                source = source,
                                currency = currency,
                                receivedDate = receivedDate,
                                note = note.trim()
                            )
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !isSaving,
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme
                            .colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isEditMode) {
                            "Update Income"
                        } else {
                            "Save Income"
                        }
                    )
                }
            }

            // -------------------------------------
            // 8. CANCEL BUTTON
            // -------------------------------------
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                Text("Cancel")
            }

            Spacer(
                modifier = Modifier.height(20.dp)
            )
        }
    }
}
