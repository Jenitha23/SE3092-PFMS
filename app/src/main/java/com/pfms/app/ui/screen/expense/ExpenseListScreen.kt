package com.pfms.app.ui.screen.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.Expense
import java.math.BigDecimal

@Composable
fun ExpenseListScreen(
    expenses: List<Expense>,
    isLoading: Boolean,
    isDeleting: Boolean,
    onAddExpense: () -> Unit,
    onQuickAdd: () -> Unit,
    onEditExpense: (String) -> Unit,
    onDeleteExpense: (String) -> Unit,
    onBack: () -> Unit,
    errorMessage: String? = null
) {
    var expenseToDelete by remember {
        mutableStateOf<Expense?>(null)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Expenses",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onQuickAdd
            ) {
                Text("+ Quick Add")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
        ) {
            Button(
                onClick = onAddExpense,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Expense")
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                expenses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No expenses recorded yet.")
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp),
                        contentPadding =
                            PaddingValues(
                                top = 12.dp,
                                bottom = 100.dp
                            )
                    ) {
                        items(
                            items = expenses,
                            key = { it.id }
                        ) { expense ->

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = expense.categoryName,
                                        style = MaterialTheme
                                            .typography.titleMedium
                                    )

                                    Text(
                                        text = "${expense.currency} ${
                                            BigDecimal.valueOf(
                                                expense.amountMinor,
                                                2
                                            ).toPlainString()
                                        }",
                                        style = MaterialTheme
                                            .typography.titleLarge
                                    )

                                    Text(
                                        "${expense.date} • " +
                                                expense.classification.name
                                    )

                                    Text(
                                        expense.paymentMethod.name
                                            .replace("_", " ")
                                    )

                                    if (expense.merchant.isNotBlank()) {
                                        Text(expense.merchant)
                                    }

                                    if (expense.note.isNotBlank()) {
                                        Text(expense.note)
                                    }

                                    Row {
                                        TextButton(
                                            onClick = {
                                                onEditExpense(expense.id)
                                            }
                                        ) {
                                            Text("Edit")
                                        }

                                        TextButton(
                                            enabled = !isDeleting,
                                            onClick = {
                                                expenseToDelete = expense
                                            }
                                        ) {
                                            Text("Delete")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) expenseToDelete = null
            },
            title = { Text("Delete Expense?") },
            text = {
                Text(
                    "Are you sure you want to delete " +
                            "this expense? This action cannot " +
                            "be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        expenseToDelete = null
                        onDeleteExpense(expense.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        expenseToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
