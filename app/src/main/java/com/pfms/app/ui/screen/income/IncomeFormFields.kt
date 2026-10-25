package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.viewmodel.IncomeFormState

@Composable
internal fun IncomeFormAmountCard(state: IncomeFormState, actions: IncomeFormActions) {
    val colors = MaterialTheme.colorScheme
    IncomeFormCard(Modifier.testTag("income_form_amount_card")) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically) {
            IncomeFormLabel("AMOUNT & CURRENCY")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("LKR", "USD").forEach { currency ->
                    FilterChip(selected = state.currency == currency, onClick = { actions.onCurrency(currency) }, enabled = !state.isSaving,
                        label = { Text(currency, style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer, selectedLabelColor = colors.primary))
                }
            }
        }
        OutlinedTextField(value = state.amount, onValueChange = actions.onAmount,
            placeholder = { Text("0.00") }, prefix = {
                Text(state.currency, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            },
            singleLine = true, enabled = !state.isSaving, isError = state.amountError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            shape = RoundedCornerShape(13.dp), colors = incomeFormFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("income_form_amount").semantics { contentDescription = "Income amount" })
        state.amountError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
        state.amountSummary?.let {
            IncomeAmount(it, colors.onSurface, Modifier.testTag("income_form_full_amount"), compact = true)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.quickAmounts.forEach { amount ->
                OutlinedButton(onClick = { actions.onIncrement(amount.majorUnits) }, enabled = !state.isSaving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)) {
                    Text(amount.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (state.isUsd) {
            HorizontalDivider(color = colors.outlineVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
                IncomeFormLabel("LKR EQUIVALENT")
                Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isRateFetching) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Text(state.rateLabel, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    }
                }
            }
            state.convertedAmount?.let {
                IncomeAmount(it, colors.primary, Modifier.testTag("income_form_conversion"))
            }
            state.rateDetail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            state.rateNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            if (state.manualRateVisible) {
                IncomeFormTextField(state.manualRate, actions.onManualRate, "e.g. 302.50", "USD to LKR rate",
                    enabled = !state.isSaving, keyboard = KeyboardType.Decimal)
                Text("LKR for 1 USD on the selected income date", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = actions.onRefreshRate, enabled = !state.isSaving && !state.isRateFetching,
                    contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Refresh rate", style = MaterialTheme.typography.labelMedium) }
                if (!state.manualRateVisible) TextButton(onClick = actions.onEnableManualRate, enabled = !state.isSaving,
                    contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Enter manually", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
internal fun IncomeFreelanceFields(state: IncomeFormState, actions: IncomeFormActions) {
    IncomeFormCard {
        IncomeFormLabel("FREELANCE DETAILS")
        IncomeFormTextField(state.projectName, actions.onProject, "Project or client name", "Project / client name",
            enabled = !state.isSaving, error = state.projectError)
        IncomeFormLabel("PAYMENT STATUS")
        IncomeFormOptions(listOf("Received", "Expected"), state.paymentStatus, actions.onStatus, !state.isSaving)
        Text("Expected payments are excluded from realized income until received.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun IncomeCryptoFields(state: IncomeFormState, actions: IncomeFormActions) {
    IncomeFormCard {
        IncomeFormLabel("CRYPTO RESULT")
        IncomeFormOptions(listOf("Gain", "Loss"), state.cryptoType, actions.onCryptoType, !state.isSaving)
        Text("Enter a positive amount. Losses are deducted from realized income.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IncomeFormLabel("ASSET")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.assets.forEach { asset ->
                FilterChip(selected = state.cryptoAsset == asset, onClick = { actions.onAsset(asset) }, enabled = !state.isSaving,
                    label = { Text(asset, style = MaterialTheme.typography.labelMedium) }, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        IncomeFormTextField(state.cryptoAsset, actions.onAsset, "e.g. BTC", "Crypto asset", enabled = !state.isSaving)
    }
}

@Composable
private fun IncomeFormOptions(options: List<String>, selected: String, onSelect: (String) -> Unit, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Surface(onClick = { onSelect(option) }, enabled = enabled, selected = selected == option,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                color = if (selected == option) colors.primaryContainer else colors.surfaceVariant,
                contentColor = if (selected == option) colors.primary else colors.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected == option) colors.primary.copy(alpha = .4f) else colors.outlineVariant)) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(option, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun IncomeFormTextField(value: String, onValue: (String) -> Unit, placeholder: String, description: String,
    enabled: Boolean = true, singleLine: Boolean = true, error: String? = null, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(value = value, onValueChange = onValue, placeholder = { Text(placeholder) },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2, maxLines = if (singleLine) 1 else 5,
        enabled = enabled, isError = error != null, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(12.dp), colors = incomeFormFieldColors(),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description })
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun incomeFormFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)
