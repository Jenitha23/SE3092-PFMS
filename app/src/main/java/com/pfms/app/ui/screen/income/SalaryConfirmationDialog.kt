package com.pfms.app.ui.screen.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pfms.app.domain.model.SalaryTemplate
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.SalaryConfirmationViewModel

@Composable
fun SalaryConfirmationDialog(template: SalaryTemplate, onConfirm: (String) -> Unit, onDismiss: () -> Unit,
    isSaving: Boolean = false, errorMessage: String? = null) = FinanceTheme {
    val controller: SalaryConfirmationViewModel = viewModel(key = "salary-confirm-${template.id}")
    LaunchedEffect(template.id) { controller.sync(template.id, template.amountMinor) }
    val state by controller.state.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = { if (!isSaving) onDismiss() },
        icon = { FinanceIcon(FinanceSymbol.INCOME) },
        title = { Text("Confirm Monthly Salary", style = MaterialTheme.typography.titleMedium) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Have you received your salary this month? Confirm or update the actual amount.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(state.amount, controller::edit, label = { Text("Salary Amount (LKR)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !isSaving, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp), isError = state.error != null, supportingText = state.error?.let { { Text(it) } })
            Text("Adjust the amount for any bonuses or deductions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            errorMessage?.let { ErrorBanner(it) }
        } }, confirmButton = {
            Button(onClick = { controller.confirm(onConfirm) }, enabled = !isSaving, shape = RoundedCornerShape(12.dp)) {
                if (isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Confirm")
            }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Not Yet") } })
}
