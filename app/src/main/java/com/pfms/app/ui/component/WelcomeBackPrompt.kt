package com.pfms.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.FinancialContextViewModel

@Composable
fun WelcomeBackIndicator(onAddIncome: () -> Unit, onAddExpense: () -> Unit,
    viewModel: FinancialContextViewModel = hiltViewModel()) {
    val show by viewModel.showWelcomeBack.collectAsStateWithLifecycle()
    if (show) WelcomeBackPrompt(onDismiss = viewModel::dismissWelcome,
        onAddIncome = { viewModel.dismissWelcome(); onAddIncome() },
        onAddExpense = { viewModel.dismissWelcome(); onAddExpense() })
}

@Composable
fun WelcomeBackPrompt(onDismiss: () -> Unit, onAddIncome: () -> Unit, onAddExpense: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Welcome back! Ready to record your recent finances?", style = MaterialTheme.typography.titleSmall)
            // Separate rows keep all actions readable with larger font settings.
            OutlinedButton(onClick = onAddIncome, modifier = Modifier.fillMaxWidth()) { Text("Add Income") }
            OutlinedButton(onClick = onAddExpense, modifier = Modifier.fillMaxWidth()) { Text("Add Expense") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
