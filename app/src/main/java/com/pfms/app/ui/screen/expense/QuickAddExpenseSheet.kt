
package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.ExpenseClassification
import com.pfms.app.domain.model.ExpensePaymentMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddExpenseSheet(
    categories: List<ExpenseCategoryOption>,
    defaultPaymentMethod: ExpensePaymentMethod,
    isSaving: Boolean,
    onSave: (
        amount: String,
        categoryId: String,
        categoryName: String,
        categoryDefault: ExpenseClassification
    ) -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    var amount by remember {
        mutableStateOf("")
    }

    var selectedCategoryId by remember(categories) {
        mutableStateOf(categories.firstOrNull()?.id ?: "")
    }

    var categoryExpanded by remember {
        mutableStateOf(false)
    }

    val category = categories.find {
        it.id == selectedCategoryId
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Quick Add Expense",
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
                    onClick = {
                        categoryExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(category?.name ?: "Select Category")
                }

                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = {
                        categoryExpanded = false
                    }
                ) {
                    categories.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                selectedCategoryId = option.id
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                "Date: Today\n" +
                        "Payment: ${
                            defaultPaymentMethod.name
                                .replace("_", " ")
                        }\n" +
                        "Classification: ${
                            category?.defaultClassification?.name
                                ?: "-"
                        }",
                style = MaterialTheme.typography.bodySmall
            )

            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val selected = category
                        ?: return@Button

                    onSave(
                        amount,
                        selected.id,
                        selected.name,
                        selected.defaultClassification
                    )
                },
                enabled = !isSaving &&
                        category != null &&
                        amount.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Expense")
                }
            }

            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
