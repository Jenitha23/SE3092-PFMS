
package com.pfms.app.ui.screen.income

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.SalaryTemplate
import java.math.BigDecimal

@Composable
fun SalaryConfirmationDialog(
    template: SalaryTemplate,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    isSaving: Boolean = false,
    errorMessage: String? = null
) {
    // Pre-fill the salary amount from the template.
    var amount by rememberSaveable(template.id) {
        mutableStateOf(
            BigDecimal.valueOf(
                template.amountMinor,
                2
            ).toPlainString()
        )
    }

    var amountError by remember {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Confirm Monthly Salary",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Have you received your " +
                            "salary this month? Confirm " +
                            "or update the actual amount."
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        amountError = null
                    },
                    label = {
                        Text("Salary Amount (LKR)")
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
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "You can change this amount " +
                            "if your salary varies due to " +
                            "bonuses or deductions.",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSaving,
                onClick = {
                    val value = amount.trim()
                    val decimal = value.toBigDecimalOrNull()

                    val validAmount =
                        value.matches(
                            Regex("""\d+(\.\d{1,2})?""")
                        ) &&
                                decimal != null &&
                                decimal > BigDecimal.ZERO &&
                                decimal <= BigDecimal(
                            "92233720368547758.07"
                        )

                    if (!validAmount) {
                        amountError =
                            "Enter a valid positive amount " +
                                    "with up to 2 decimal places."
                    } else {
                        onConfirm(value)
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color =
                            MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss
            ) {
                Text("Not Yet")
            }
        }
    )
}
