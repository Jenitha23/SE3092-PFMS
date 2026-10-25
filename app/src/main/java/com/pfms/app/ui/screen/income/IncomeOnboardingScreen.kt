package com.pfms.app.ui.screen.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.IncomeOnboardingState

@Composable
fun IncomeOnboardingScreen(state: IncomeOnboardingState, onSave: (String) -> Unit) = FinanceTheme {
    var amount by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FinanceBrand()
                Text("Let's set up your income", style = MaterialTheme.typography.headlineSmall)
                Text("A monthly estimate helps you compare your plan with your recorded income.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FinancePanel {
                    Text("Your starting point", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(amount, { amount = it }, label = { Text("Estimated monthly income (LKR)") },
                        enabled = !state.saving, singleLine = true, shape = RoundedCornerShape(13.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Text("Enter 0 if you have no regular income yet. You can update this in Income Analytics.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { ErrorBanner(it) }
                    Button(onClick = { onSave(amount) }, enabled = !state.saving, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (state.saving) "Saving…" else if (state.canRetry && state.error != null) "Retry" else "Continue")
                    }
                }
            }
        }
    }
}
