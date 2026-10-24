package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.PfmsPasswordField
import com.pfms.app.ui.component.PfmsTextField
import com.pfms.app.ui.theme.AppThemeMode
import com.pfms.app.viewmodel.*

@Composable
internal fun SettingsDialogs(
    editor: SettingsEditorState,
    state: SettingsUiState,
    profile: SettingsProfileState,
    theme: AppThemeMode,
    paymentMethods: List<String>,
    onDismiss: () -> Unit,
    onEdit: (SettingsField, String) -> Unit,
    onSaveName: () -> Unit,
    onSavePassword: () -> Unit,
    onDelete: () -> Unit,
    onPayment: (String?) -> Unit,
    onTheme: (AppThemeMode) -> Unit,
    reminderContent: @Composable () -> Unit
) {
    val dialog = editor.dialog ?: return
    val title = when (dialog) {
        SettingsDialog.NAME -> "Edit full name"
        SettingsDialog.PAYMENT -> "Default payment method"
        SettingsDialog.THEME -> "Choose theme"
        SettingsDialog.PASSWORD -> "Change password"
        SettingsDialog.DELETE -> "Delete account"
        SettingsDialog.REMINDERS -> "Reminder schedules"
    }
    AlertDialog(onDismissRequest = { if (!state.isLoading) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                ErrorBanner(editor.error ?: state.errorMessage)
                when (dialog) {
                    SettingsDialog.NAME -> PfmsTextField(editor.name, { onEdit(SettingsField.NAME, it) },
                        label = "Full name", enabled = !state.isLoading)
                    SettingsDialog.PASSWORD -> {
                        PfmsPasswordField(editor.currentPassword, { onEdit(SettingsField.CURRENT_PASSWORD, it) },
                            label = "Current password", enabled = !state.isLoading)
                        PfmsPasswordField(editor.newPassword, { onEdit(SettingsField.NEW_PASSWORD, it) },
                            label = "New password", enabled = !state.isLoading)
                        PfmsPasswordField(editor.confirmPassword, { onEdit(SettingsField.CONFIRM_PASSWORD, it) },
                            label = "Confirm new password", enabled = !state.isLoading)
                    }
                    SettingsDialog.DELETE -> {
                        Text("This action is permanent. Your account and financial data will be permanently erased.")
                        if (profile.googleOnly) Text("Authenticate with Google to confirm deletion.")
                        else PfmsPasswordField(editor.deletePassword, { onEdit(SettingsField.DELETE_PASSWORD, it) },
                            label = "Account password", enabled = !state.isLoading)
                    }
                    SettingsDialog.PAYMENT -> {
                        Text("Choose your preferred way to pay.", style = MaterialTheme.typography.bodyMedium)
                        SettingsChoice("No default", profile.paymentMethod == "Choose a payment method", !state.isLoading) { onPayment(null) }
                        paymentMethods.forEach { method ->
                            SettingsChoice(method, profile.paymentMethod == method, !state.isLoading) { onPayment(method) }
                        }
                    }
                    SettingsDialog.THEME -> AppThemeMode.entries.forEach { mode ->
                        SettingsChoice(mode.getDisplayName(), theme == mode) { onTheme(mode) }
                    }
                    SettingsDialog.REMINDERS -> reminderContent()
                }
            }
        },
        confirmButton = {
            when (dialog) {
                SettingsDialog.NAME -> Button(onClick = onSaveName, enabled = !state.isLoading && editor.canSaveName) { Text("Save name") }
                SettingsDialog.PASSWORD -> Button(onClick = onSavePassword, enabled = !state.isLoading && editor.canSavePassword) { Text("Update password") }
                SettingsDialog.DELETE -> Button(onClick = onDelete, enabled = !state.isLoading && (profile.googleOnly || editor.deletePassword.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text(if (profile.googleOnly) "Re-auth & delete" else "Delete account")
                }
                else -> TextButton(onClick = onDismiss, enabled = !state.isLoading) { Text("Done") }
            }
        },
        dismissButton = {
            if (dialog == SettingsDialog.NAME || dialog == SettingsDialog.PASSWORD || dialog == SettingsDialog.DELETE)
                TextButton(onClick = onDismiss, enabled = !state.isLoading) { Text("Cancel") }
        })
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(label, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
