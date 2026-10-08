package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.InfoBanner
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsPasswordField
import com.pfms.app.ui.component.PfmsTextField
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.viewmodel.SettingsViewModel

/**
 * Settings and Profile management screen for PFMS (FR-06).
 *
 * Implements:
 * 1. Profile: Editable display name and current email display.
 * 2. Preferences: Default payment method picker and Biometric app-lock switch (FR-04).
 * 3. Security: Change Password (email/password accounts), Logout with unsynced data check (FR-05),
 *    and Account Deletion with re-authentication.
 * 4. App Info: Real versionName display.
 */
@Composable
fun SettingsScreen(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val user by settingsViewModel.user.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val biometricEnabled by settingsViewModel.biometricEnabled.collectAsStateWithLifecycle()
    val logoutResult by sessionViewModel.logoutResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isBiometricHardwareAvailable = remember { settingsViewModel.isBiometricAvailable() }

    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var hasInitializedName by rememberSaveable { mutableStateOf(false) }

    var paymentMenuExpanded by remember { mutableStateOf(false) }
    var showChangePasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteAccountDialog by rememberSaveable { mutableStateOf(false) }

    // Sync display name once user or profile is initially loaded
    LaunchedEffect(profile?.displayName, user?.displayName) {
        if (!hasInitializedName) {
            val initial = profile?.displayName ?: user?.displayName
            if (!initial.isNullOrBlank()) {
                displayNameInput = initial
                hasInitializedName = true
            }
        }
    }

    // Auto-dismiss password dialog upon successful password change
    LaunchedEffect(uiState.message) {
        if (uiState.message == "Password changed.") {
            showChangePasswordDialog = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ── Top Header ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings & Profile",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Feedback Banners ───────────────────────────────────────
            val logoutErrorMessage = (logoutResult as? LogoutResult.Failure)?.message
            val activeErrorMessage = uiState.errorMessage ?: logoutErrorMessage

            ErrorBanner(message = activeErrorMessage)
            if (activeErrorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            InfoBanner(message = uiState.message)
            if (uiState.message != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Section 1: Profile ─────────────────────────────────────
            SectionHeader(title = "Profile")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Email (Read-only)
                    Text(
                        text = "Email Address",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = user?.email ?: profile?.email ?: "No email available",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Editable Display Name
                    PfmsTextField(
                        value = displayNameInput,
                        onValueChange = {
                            displayNameInput = it
                            if (uiState.errorMessage != null || uiState.message != null) {
                                settingsViewModel.clearMessages()
                            }
                        },
                        label = "Display Name",
                        enabled = !uiState.isLoading,
                        leadingIcon = {
                            Text("👤", style = MaterialTheme.typography.bodyLarge)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val currentPersistedName = profile?.displayName ?: user?.displayName ?: ""
                    val isNameModified = displayNameInput.trim() != currentPersistedName.trim() && displayNameInput.isNotBlank()

                    PfmsButton(
                        text = "Save Name",
                        onClick = {
                            focusManager.clearFocus()
                            settingsViewModel.updateDisplayName(displayNameInput.trim())
                        },
                        enabled = !uiState.isLoading && isNameModified,
                        isLoading = uiState.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 2: Preferences ─────────────────────────────────
            SectionHeader(title = "Preferences")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Default Payment Method Selector
                    Text(
                        text = "Default Payment Method",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box {
                        OutlinedCard(
                            onClick = { if (!uiState.isLoading) paymentMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💳", style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = profile?.defaultPaymentMethod ?: "Select a method",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "▼",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = paymentMenuExpanded,
                            onDismissRequest = { paymentMenuExpanded = false }
                        ) {
                            settingsViewModel.paymentMethods.forEach { method ->
                                DropdownMenuItem(
                                    text = { Text(method) },
                                    onClick = {
                                        paymentMenuExpanded = false
                                        settingsViewModel.updateDefaultPaymentMethod(method)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Biometric Lock Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric App Lock",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    !isBiometricHardwareAvailable -> "Biometric hardware unavailable or no fingerprint/face enrolled on device."
                                    biometricEnabled -> "Active: Prompt for fingerprint/face when returning to app."
                                    else -> "Inactive: Enable to lock app on background."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!isBiometricHardwareAvailable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { settingsViewModel.setBiometricEnabled(it) },
                            enabled = isBiometricHardwareAvailable && !uiState.isLoading
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 3: Security & Account ──────────────────────────
            SectionHeader(title = "Security & Account")

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    val canChangePassword = user?.canChangePassword == true

                    if (canChangePassword) {
                        OutlinedButton(
                            onClick = {
                                settingsViewModel.clearMessages()
                                showChangePasswordDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Change Password")
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Signed in with Google. Password management is handled by your Google Account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Log Out
                    OutlinedButton(
                        onClick = {
                            settingsViewModel.clearMessages()
                            sessionViewModel.logout(force = false)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Log Out")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Delete Account (Destructive)
                    Button(
                        onClick = {
                            settingsViewModel.clearMessages()
                            showDeleteAccountDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Delete Account")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Section 4: App Info ─────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PFMS Version",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = settingsViewModel.appVersion.ifBlank { "1.0.0" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // ── Dialog: Change Password ────────────────────────────────────────
    if (showChangePasswordDialog) {
        var currentPassword by rememberSaveable { mutableStateOf("") }
        var newPassword by rememberSaveable { mutableStateOf("") }
        var confirmPassword by rememberSaveable { mutableStateOf("") }
        var clientValidationError by rememberSaveable { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isLoading) {
                    showChangePasswordDialog = false
                    clientValidationError = null
                }
            },
            title = {
                Text(
                    text = "Change Password",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (clientValidationError != null) {
                        ErrorBanner(message = clientValidationError)
                    }

                    PfmsPasswordField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            clientValidationError = null
                        },
                        label = "Current Password",
                        enabled = !uiState.isLoading
                    )

                    PfmsPasswordField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            clientValidationError = null
                        },
                        label = "New Password",
                        enabled = !uiState.isLoading
                    )

                    PfmsPasswordField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            clientValidationError = null
                        },
                        label = "Confirm New Password",
                        enabled = !uiState.isLoading
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            confirmPassword.isBlank() -> {
                                clientValidationError = "Please confirm your new password."
                            }
                            newPassword != confirmPassword -> {
                                clientValidationError = "New password and confirmation do not match."
                            }
                            else -> {
                                clientValidationError = null
                                settingsViewModel.changePassword(currentPassword, newPassword)
                            }
                        }
                    },
                    enabled = !uiState.isLoading && currentPassword.isNotBlank() && newPassword.isNotBlank()
                ) {
                    Text("Update Password")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showChangePasswordDialog = false
                        clientValidationError = null
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Dialog: Unsynchronized Data Warning (Logout) ───────────────────
    if (logoutResult is LogoutResult.UnsynchronizedDataWarning) {
        AlertDialog(
            onDismissRequest = { sessionViewModel.consumeLogoutResult() },
            icon = { Text("⚠", style = MaterialTheme.typography.headlineSmall) },
            title = { Text("Unsynchronized Data") },
            text = {
                Text(
                    "You have offline financial entries that have not synced with the cloud yet. " +
                    "Logging out will discard unsaved local changes. Do you still want to log out?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionViewModel.logout(force = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionViewModel.consumeLogoutResult() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Dialog: Delete Account Confirmation ────────────────────────────
    if (showDeleteAccountDialog) {
        var deletePasswordInput by rememberSaveable { mutableStateOf("") }
        val isGoogleUser = user?.isGoogleAccount == true && user?.canChangePassword == false

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isLoading) showDeleteAccountDialog = false
            },
            icon = { Text("⚠", style = MaterialTheme.typography.headlineMedium) },
            title = {
                Text(
                    text = "Delete Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "This action is permanent. All your financial data, accounts, and preferences will be permanently erased.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isGoogleUser) {
                        Text(
                            text = "To confirm deletion, please authenticate with your Google account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "To confirm deletion, please enter your account password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PfmsPasswordField(
                            value = deletePasswordInput,
                            onValueChange = { deletePasswordInput = it },
                            label = "Account Password",
                            enabled = !uiState.isLoading
                        )
                    }
                }
            },
            confirmButton = {
                if (isGoogleUser) {
                    Button(
                        onClick = {
                            settingsViewModel.deleteAccountWithGoogle(context)
                        },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Re-auth & Delete")
                    }
                } else {
                    Button(
                        onClick = {
                            if (deletePasswordInput.isNotBlank()) {
                                settingsViewModel.deleteAccountWithPassword(deletePasswordInput)
                            }
                        },
                        enabled = !uiState.isLoading && deletePasswordInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Account")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false },
                    enabled = !uiState.isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
