package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    errorMessage: String? = null,
    canRetry: Boolean = false
) {
    ExpenseUiTheme {
        QuickAddExpenseSheetContent(categories, defaultPaymentMethod, isSaving, onSave, onDismiss, errorMessage, canRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddExpenseSheetContent(
    categories: List<ExpenseCategoryOption>, defaultPaymentMethod: ExpensePaymentMethod,
    isSaving: Boolean, onSave: (String, String, String, ExpenseClassification) -> Unit,
    onDismiss: () -> Unit, errorMessage: String?, canRetry: Boolean
) {
    var amount by rememberSaveable {
        mutableStateOf("")
    }

    var selectedCategoryId by rememberSaveable {
        mutableStateOf(categories.firstOrNull()?.id ?: "")
    }

    LaunchedEffect(categories) { if (selectedCategoryId.isBlank()) selectedCategoryId = categories.firstOrNull()?.id.orEmpty() }

    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val category = categories.find {
        it.id == selectedCategoryId
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        }
    ) {
        LaunchedEffect(Unit) { amountFocus.requestFocus(); keyboard?.show() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {
            ExpenseSectionTitle("Quick Add Expense", ExpenseSymbol.ADD)

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (LKR)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        val selected = category ?: return@KeyboardActions
                        if (!isSaving && amount.isNotBlank()) {
                            keyboard?.hide()
                            onSave(
                                amount,
                                selected.id,
                                selected.name,
                                selected.defaultClassification
                            )
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(amountFocus)
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { option ->
                    FilterChip(selected = category?.id == option.id, enabled = !isSaving,
                        onClick = { selectedCategoryId = option.id }, label = { Text(option.name) })
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
                    Text(if (canRetry && errorMessage != null) "Retry" else "Save Expense")
                }
            }

            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
