package com.pfms.app.ui.screen.income

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pfms.app.domain.model.SalaryTemplate
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.SalaryFormViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryTemplateScreen(initialTemplate: SalaryTemplate? = null, onBack: () -> Unit,
    onSaveTemplate: (String) -> Unit, modifier: Modifier = Modifier, isSaving: Boolean = false,
    errorMessage: String? = null, onRetry: (() -> Unit)? = null,
    onSaveSchedule: ((String, Int, Boolean) -> Unit)? = null) = FinanceTheme {
    val vm: SalaryFormViewModel = viewModel(key = "salary-form-${initialTemplate?.id ?: "new"}",
        factory = viewModelFactory { initializer { SalaryFormViewModel(createSavedStateHandle(), initialTemplate) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(isSaving, errorMessage, onRetry != null) { vm.backend(isSaving, errorMessage, onRetry != null) }
    val colors = MaterialTheme.colorScheme
    BackHandler { if (!state.saving) onBack() }
    Scaffold(modifier.imePadding(), containerColor = colors.background,
        topBar = { TopAppBar(title = { Text("Recurring Salary", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !state.saving) { FinanceIcon(FinanceSymbol.BACK, description = "Back") } },
            actions = { TextButton(onClick = { vm.help(true) }) { Text("Help") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)) },
        bottomBar = { FinanceSaveBar(state.saveLabel, state.saving, state.error,
            { vm.submit { amount, day, enabled -> onSaveSchedule?.invoke(amount, day, enabled) ?: onSaveTemplate(amount) } }, onBack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Set Up Your Salary", style = MaterialTheme.typography.headlineSmall)
                Text("Save your usual monthly salary and choose when to confirm your actual payout.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                FinancePanel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("USUAL SALARY AMOUNT", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                        Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer) {
                            Text("LKR", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                    }
                    OutlinedTextField(state.amount, vm::amount, Modifier.fillMaxWidth(), label = { Text("Salary amount (LKR)") },
                        prefix = { Text("LKR  ") }, singleLine = true, enabled = !state.saving,
                        textStyle = MaterialTheme.typography.headlineSmall, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(13.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = colors.surfaceVariant,
                            unfocusedContainerColor = colors.surfaceVariant, unfocusedBorderColor = colors.outlineVariant))
                    state.formatted?.let { FinanceValue(it, Modifier.testTag("salary_form_value")) }
                    Text("The template is a reference amount. Confirm each payout before it is recorded as income.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                FinancePanel {
                    Text("PAYOUT SCHEDULE", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Surface(onClick = { vm.picker(true) }, enabled = !state.saving, shape = RoundedCornerShape(12.dp), color = colors.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FinanceIcon(FinanceSymbol.INCOME)
                            Column(Modifier.weight(1f)) {
                                Text(state.dayLabel, style = MaterialTheme.typography.titleSmall)
                                Text(state.nextPayday, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                            Text("Change", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                        }
                    }
                    Text("For shorter months, payday falls on the last day of the month.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Payday confirmation prompt", style = MaterialTheme.typography.titleSmall)
                            Text("Ask me to confirm my salary when it is due.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Switch(state.enabled, vm::enabled, enabled = !state.saving)
                    }
                }
            }
        }
    }
    if (state.dayPicker) AlertDialog(onDismissRequest = { vm.picker(false) }, title = { Text("Choose payday") },
        text = { FlowRow(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..31).forEach { day -> FilterChip(selected = state.day == day, onClick = { vm.day(day) }, label = { Text(day.toString()) }, modifier = Modifier.heightIn(min = 48.dp)) }
        } }, confirmButton = { TextButton(onClick = { vm.picker(false) }) { Text("Done") } })
    if (state.help) AlertDialog(onDismissRequest = { vm.help(false) }, title = { Text("Recurring salary") },
        text = { Text("This template does not add income automatically. When the reminder is enabled, the app asks you to confirm your actual salary on or after payday. You can change the amount before confirming.") },
        confirmButton = { TextButton(onClick = { vm.help(false) }) { Text("Got it") } })
}
