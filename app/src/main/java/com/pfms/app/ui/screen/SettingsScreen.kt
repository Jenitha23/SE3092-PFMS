package com.pfms.app.ui.screen

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.model.ReminderType
import com.pfms.app.ui.component.ReminderSettingsSection
import com.pfms.app.ui.theme.ThemeViewModel
import com.pfms.app.viewmodel.*

/** Route adapter. All account changes and validation are handled by ViewModels. */
@Composable
fun SettingsScreen(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToPrivacyPolicy: () -> Unit = {},
    reminderViewModel: ReminderSettingsViewModel = hiltViewModel()
) {
    val state by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profileState.collectAsStateWithLifecycle()
    val editor by settingsViewModel.editor.collectAsStateWithLifecycle()
    val biometric by settingsViewModel.biometricEnabled.collectAsStateWithLifecycle()
    val logout by sessionViewModel.logoutResult.collectAsStateWithLifecycle()
    val theme by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val reminders by reminderViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val biometricAvailable by settingsViewModel.biometricAvailable.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        settingsViewModel.refreshBiometricAvailability()
        reminderViewModel.refreshPermission()
    }
    SettingsScreenContent(profile, state, biometric, biometricAvailable, reminders, theme, settingsViewModel.appVersion,
        logoutError = (logout as? LogoutResult.Failure)?.message,
        actions = SettingsActions(
            onBack = onNavigateBack, onDialog = settingsViewModel::openDialog,
            onBiometric = settingsViewModel::setBiometricEnabled,
            onDailyReminder = { reminderViewModel.setEnabled(ReminderType.DAILY_LOG, it) },
            onPrivacy = onNavigateToPrivacyPolicy,
            onLogout = { settingsViewModel.clearMessages(); sessionViewModel.logout(force = false) },
            onRetry = settingsViewModel::retry, onReminderRetry = reminderViewModel::retry
        ), dialogs = {
            SettingsDialogs(editor, state, profile, theme, settingsViewModel.paymentMethods,
                settingsViewModel::dismissDialog, settingsViewModel::edit,
                settingsViewModel::saveName, settingsViewModel::savePassword,
                onDelete = { settingsViewModel.deleteFromEditor(context) },
                onPayment = settingsViewModel::updateDefaultPaymentMethod,
                onTheme = { themeViewModel.setThemeMode(it); settingsViewModel.dismissDialog() },
                reminderContent = { ReminderSettingsSection(reminderViewModel) })
            if (logout is LogoutResult.UnsynchronizedDataWarning) {
                AlertDialog(onDismissRequest = sessionViewModel::consumeLogoutResult,
                    title = { Text("Unsynchronized data") },
                    text = { Text("You have offline financial entries that have not synced with the cloud yet. " +
                        "Logging out will discard unsaved local changes. Do you still want to log out?") },
                    confirmButton = {
                        TextButton(onClick = { sessionViewModel.logout(force = true) }) { Text("Log Out Anyway") }
                    },
                    dismissButton = {
                        TextButton(onClick = sessionViewModel::consumeLogoutResult) { Text("Cancel") }
                    })
            }
        })
}
