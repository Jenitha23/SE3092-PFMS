package com.pfms.app.ui.screen.income

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
import com.pfms.app.viewmodel.IncomeOnboardingState

@Composable
fun IncomeOnboardingScreen(state: IncomeOnboardingState, onSave: (String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Let's set up your income", style = MaterialTheme.typography.headlineMedium)
        Text("What do you expect to earn each month? Enter your estimate in LKR. You can update it in Income Analytics. Enter 0 if you have no regular income yet.")
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Estimated monthly income (LKR)") },
            enabled = !state.saving, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(amount) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.saving) "Saving…" else if (state.canRetry && state.error != null) "Retry" else "Continue")
        }
    }
}
