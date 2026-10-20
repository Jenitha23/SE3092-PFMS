
package com.pfms.app.ui.screen.income

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.SalaryTemplate
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryTemplateScreen(
    initialTemplate: SalaryTemplate? = null,
    onBack: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    var amount by rememberSaveable(initialTemplate?.id) {
        mutableStateOf(
            initialTemplate?.amountMinor?.let {
                BigDecimal.valueOf(it, 2).toPlainString()
            } ?: ""
        )
    }

    var amountError by remember {
        mutableStateOf<String?>(null)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Recurring Salary",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Text(
                text = "Set Up Your Salary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Save your usual salary amount. " +
                        "On or after the 25th of each month, " +
                        "you can confirm the actual amount " +
                        "you received.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = amount,
                onValueChange = {
                    amount = it
                    amountError = null
                },
                label = {
                    Text("Usual Salary Amount (LKR)")
                },
                placeholder = {
                    Text("130000.00")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                isError = amountError != null,
                supportingText = {
                    amountError?.let {
                        Text(it)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE9EDF2)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Salary Day",
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "25th of every month",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Your salary will not be " +
                                "recorded automatically. " +
                                "You must confirm it first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            com.pfms.app.ui.component.ErrorBanner(errorMessage, onRetry = if (isSaving) null else onRetry)

            Button(
                onClick = {
                    val value = amount.trim()
                    val parsed = value.toBigDecimalOrNull()

                    val isValid =
                        value.matches(
                            Regex("""\d+(\.\d{1,2})?""")
                        ) &&
                                parsed != null &&
                                parsed > BigDecimal.ZERO &&
                                parsed <= BigDecimal(
                            "92233720368547758.07"
                        )

                    if (!isValid) {
                        amountError =
                            "Enter a positive amount with " +
                                    "up to 2 decimal places."
                    } else {
                        onSaveTemplate(value)
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (initialTemplate == null) {
                            "Create Salary Template"
                        } else {
                            "Update Salary Template"
                        }
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
