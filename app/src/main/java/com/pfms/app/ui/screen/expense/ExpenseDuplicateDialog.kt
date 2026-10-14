
package com.pfms.app.ui.screen.expense

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun ExpenseDuplicateDialog(
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        title = {
            Text("Possible Duplicate Expense")
        },
        text = {
            Text(
                "An identical expense was recorded " +
                        "within the last 60 seconds. " +
                        "Do you want to save another?"
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onConfirm
            ) {
                Text("Save Anyway")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSaving,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
