package com.pfms.app.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.AppThemeMode
import com.pfms.app.ui.theme.FinanceTheme
import com.pfms.app.viewmodel.*

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onDialog: (SettingsDialog) -> Unit = {},
    val onBiometric: (Boolean) -> Unit = {},
    val onDailyReminder: (Boolean) -> Unit = {},
    val onPrivacy: () -> Unit = {},
    val onLogout: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onReminderRetry: () -> Unit = {}
)

/** Displays prepared state and forwards input; contains no persistence or financial rules. */
@Composable
fun SettingsScreenContent(
    profile: SettingsProfileState,
    state: SettingsUiState = SettingsUiState(),
    biometricEnabled: Boolean = false,
    biometricAvailable: Boolean = false,
    reminders: ReminderSettingsUiState = ReminderSettingsUiState(),
    theme: AppThemeMode = AppThemeMode.SYSTEM,
    version: String = "",
    logoutError: String? = null,
    actions: SettingsActions = SettingsActions(),
    dialogs: @Composable () -> Unit = {}
) = FinanceTheme {
    val colors = MaterialTheme.colorScheme
    Scaffold(containerColor = colors.background, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = colors.background) {
                Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onBack) { FinanceIcon(FinanceSymbol.BACK, description = "Back") }
                    Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Surface(shape = CircleShape, color = colors.primaryContainer, modifier = Modifier.padding(end = 8.dp)) {
                        FinanceIcon(FinanceSymbol.SHIELD, Modifier.padding(10.dp))
                    }
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize().navigationBarsPadding().imePadding()
                .testTag("settings_sections"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item(key = "profile") { ProfileHeader(profile, !state.isLoading) { actions.onDialog(SettingsDialog.NAME) } }
                if (state.isLoading || state.errorMessage != null || state.message != null || logoutError != null) item(key = "status") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        ErrorBanner(state.errorMessage ?: logoutError, onRetry = if (state.canRetry) actions.onRetry else null)
                        InfoBanner(state.message)
                    }
                }
                item(key = "account") {
                    SettingsGroup("ACCOUNT DETAILS") {
                        SettingsRow(FinanceSymbol.PERSON, "Full name", profile.name,
                            enabled = !state.isLoading, onClick = { actions.onDialog(SettingsDialog.NAME) })
                        SettingsDivider()
                        SettingsRow(FinanceSymbol.MAIL, "Email address", profile.email.ifBlank { "Not available" })
                    }
                }
                item(key = "preferences") {
                    SettingsGroup("PREFERENCES & FINANCE") {
                        SettingsRow(FinanceSymbol.INCOME, "Default payment method", profile.paymentMethod,
                            enabled = !state.isLoading, onClick = { actions.onDialog(SettingsDialog.PAYMENT) })
                        SettingsDivider()
                        SettingsRow(FinanceSymbol.WALLET, "Currency", profile.currency)
                        SettingsDivider()
                        SettingsRow(FinanceSymbol.SUN, "Theme", theme.getDisplayName(),
                            onClick = { actions.onDialog(SettingsDialog.THEME) })
                    }
                }
                item(key = "security") {
                    SettingsGroup("SECURITY & NOTIFICATIONS") {
                        SettingsRow(FinanceSymbol.LOCK, "Biometric lock",
                            if (biometricAvailable) "Fingerprint & face unlock" else "Set up biometrics in device settings",
                            trailing = {
                                Switch(checked = biometricEnabled, onCheckedChange = actions.onBiometric,
                                    enabled = (biometricAvailable || biometricEnabled) && !state.isLoading,
                                    modifier = Modifier.semantics { contentDescription = "Biometric lock" })
                            })
                        SettingsDivider()
                        SettingsRow(FinanceSymbol.BELL, "Daily ledger reminder", reminders.dailySummary,
                            trailing = {
                                Switch(checked = reminders.dailyEnabled, onCheckedChange = actions.onDailyReminder,
                                    enabled = reminders.canChangeDaily,
                                    modifier = Modifier.semantics { contentDescription = "Daily ledger reminder" })
                            })
                        SettingsDivider()
                        SettingsRow(FinanceSymbol.BELL, "Reminder schedules", "Times, alerts & notification access",
                            onClick = { actions.onDialog(SettingsDialog.REMINDERS) })
                        if (profile.canChangePassword) {
                            SettingsDivider()
                            SettingsRow(FinanceSymbol.SHIELD, "Change password", "Keep your account secure",
                                enabled = !state.isLoading, onClick = { actions.onDialog(SettingsDialog.PASSWORD) })
                        } else if (profile.googleOnly) {
                            SettingsDivider()
                            SettingsRow(FinanceSymbol.SHIELD, "Google sign-in", "Your password is managed by Google")
                        }
                    }
                    if (reminders.errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        ErrorBanner(reminders.errorMessage, onRetry = actions.onReminderRetry)
                    }
                }
                item(key = "privacy") {
                    SettingsGroup("PRIVACY & ACCOUNT") {
                        SettingsRow(FinanceSymbol.SHIELD, "Privacy policy", "How your information is handled", onClick = actions.onPrivacy)
                        SettingsDivider()
                        SettingsRow(FinanceSymbol.DELETE, "Delete account", "Permanently remove your account and data",
                            destructive = true, enabled = !state.isLoading, onClick = { actions.onDialog(SettingsDialog.DELETE) })
                    }
                }
                item(key = "logout") {
                    OutlinedButton(onClick = actions.onLogout, enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = .24f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.error.copy(alpha = .07f), contentColor = colors.error)) {
                        FinanceIcon(FinanceSymbol.LOGOUT, tint = colors.error)
                        Spacer(Modifier.width(10.dp))
                        Text("Log Out", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("TrackMyFinances${if (version.isBlank()) "" else " v$version"}",
                        Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
    dialogs()
}

@Composable
private fun ProfileHeader(profile: SettingsProfileState, enabled: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant), shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surface)))
            .padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("YOUR FINANCIAL SPACE", style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.6.sp, color = colors.primary)
            Box(Modifier.padding(vertical = 4.dp)) {
                Surface(shape = CircleShape, color = colors.primary.copy(alpha = .14f), modifier = Modifier.size(96.dp)) {
                    Box(Modifier.padding(5.dp)) {
                        Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(profile.initials, style = MaterialTheme.typography.headlineLarge)
                            }
                        }
                    }
                }
                FilledIconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(40.dp).align(Alignment.BottomEnd),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.surface, contentColor = colors.primary)) {
                    FinanceIcon(FinanceSymbol.EDIT, description = "Edit full name")
                }
            }
            Text(profile.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (profile.email.isNotBlank()) Text(profile.email, style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
            Surface(shape = CircleShape, color = if (profile.verified) colors.primaryContainer else colors.surfaceVariant) {
                Text(if (profile.verified) "✓  Email verified" else "Email not verified",
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium,
                    color = if (profile.verified) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium,
            letterSpacing = .7.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 1.dp) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsDivider() = HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun SettingsRow(symbol: FinanceSymbol, title: String, detail: String, enabled: Boolean = true,
    destructive: Boolean = false, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val iconTint = if (destructive) colors.error else colors.primary
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = iconTint.copy(alpha = .08f), modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { FinanceIcon(symbol, tint = iconTint) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (destructive) colors.error else colors.onSurface)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (trailing != null) trailing() else if (onClick != null) FinanceIcon(FinanceSymbol.CHEVRON, tint = colors.onSurfaceVariant)
        }
    }
    if (onClick != null) Surface(onClick = onClick, enabled = enabled, color = colors.surface, content = body)
    else Surface(color = colors.surface, content = body)
}
