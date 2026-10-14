
package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod
import com.pfms.app.viewmodel.ExpenseFormData
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ExpenseFormScreen(
    initialExpense: Expense?,
    categories: List<ExpenseCategoryOption>,
    isSaving: Boolean,
    onSave: (ExpenseFormData) -> Unit,
    onBack: () -> Unit,
    errorMessage: String? = null,
    defaultPaymentMethod: ExpensePaymentMethod =
        ExpensePaymentMethod.CASH
) {
    val editing = initialExpense != null

    var amount by remember(initialExpense?.id) {
        mutableStateOf(
            initialExpense?.let {
                BigDecimal.valueOf(
                    it.amountMinor, 2
                ).toPlainString()
            } ?: ""
        )
    }

    var selectedCategoryId by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.categoryId ?: "")
    }

    var date by remember(initialExpense?.id) {
        mutableStateOf(
            initialExpense?.date
                ?: LocalDate.now(
                    ZoneId.of("Asia/Colombo")
                ).toString()
        )
    }

    var merchant by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.merchant ?: "")
    }

    var note by remember(initialExpense?.id) {
        mutableStateOf(initialExpense?.note ?: "")
    }

    var paymentMethod by remember(initialExpense?.id) {
        mutableStateOf(
            initialExpense?.paymentMethod
                ?: defaultPaymentMethod
        )
    }

    var classificationOverride by remember(
        initialExpense?.id
    ) {
        mutableStateOf<ExpenseClassification?>(
            initialExpense?.classification
        )
    }

    var categoryExpanded by remember {
        mutableStateOf(false)
    }

    var paymentExpanded by remember {
        mutableStateOf(false)
    }

    val selectedCategory = categories.find {
        it.id == selectedCategoryId
    }

    // Preserve historical classification during editing,
    // even if Member 2 changes the category's default.
    val effectiveClassification =
        classificationOverride
            ?: selectedCategory?.defaultClassification
            ?: ExpenseClassification.DISCRETIONARY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }

        Text(
            text = if (editing) "Edit Expense"
            else "Add Expense",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount (LKR)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Box {
            OutlinedButton(
                onClick = { categoryExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    selectedCategory?.name
                        ?: "Select Category"
                )
            }

            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = {
                    categoryExpanded = false
                }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = {
                            selectedCategoryId = category.id
                            classificationOverride = null
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        Text(
            "Classification",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            "Default: ${
                selectedCategory?.defaultClassification?.name
                    ?: "Select a category"
            }"
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = effectiveClassification ==
                        ExpenseClassification.COMMITTED,
                onClick = {
                    classificationOverride =
                        ExpenseClassification.COMMITTED
                },
                label = { Text("Committed") }
            )

            FilterChip(
                selected = effectiveClassification ==
                        ExpenseClassification.DISCRETIONARY,
                onClick = {
                    classificationOverride =
                        ExpenseClassification.DISCRETIONARY
                },
                label = { Text("Discretionary") }
            )
        }

        TextButton(
            onClick = { classificationOverride = null },
            enabled = selectedCategory != null
        ) {
            Text("Use Category Default")
        }

        Box {
            OutlinedButton(
                onClick = { paymentExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Payment: " +
                            paymentMethod.name.replace("_", " ")
                )
            }

            DropdownMenu(
                expanded = paymentExpanded,
                onDismissRequest = {
                    paymentExpanded = false
                }
            ) {
                ExpensePaymentMethod.entries.forEach {
                        method ->
                    DropdownMenuItem(
                        text = {
                            Text(method.name.replace("_", " "))
                        },
                        onClick = {
                            paymentMethod = method
                            paymentExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            label = { Text("Date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (Optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            enabled = !isSaving &&
                    selectedCategory != null &&
                    amount.isNotBlank(),
            onClick = {
                val category = selectedCategory
                    ?: return@Button

                onSave(
                    ExpenseFormData(
                        amount = amount,
                        categoryId = category.id,
                        categoryName = category.name,
                        categoryDefault =
                            category.defaultClassification,
                        classificationOverride =
                            classificationOverride,
                        paymentMethod = paymentMethod,
                        date = date,
                        merchant = merchant,
                        note = note
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (editing) "Update Expense"
                    else "Save Expense"
                )
            }
        }
    }
}
