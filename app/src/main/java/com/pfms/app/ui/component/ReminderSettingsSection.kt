package com.pfms.app.ui.component

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.*
import com.pfms.app.viewmodel.*
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReminderSettingsSection(viewModel: ReminderSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermission() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermission() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    ReminderSettingsContent(state, viewModel::setEnabled, viewModel::setTime, viewModel::setWeekday, viewModel::retry,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onOpenSystemSettings = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsContent(
    state: ReminderSettingsUiState,
    onEnabled: (ReminderType, Boolean) -> Unit,
    onTime: (ReminderType, Int, Int) -> Unit,
    onWeekday: (Int) -> Unit,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val locale = LocalConfiguration.current.locales[0]
    var editingTime by remember(state.settings?.userId) { mutableStateOf<ReminderType?>(null) }
    var showWeekdays by remember(state.settings?.userId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Financial Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
        ElevatedCard(shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Times use Asia/Colombo. Android may deliver reminders later to save battery.", style = MaterialTheme.typography.bodySmall)
                Text("These switches control notifications. Salary confirmation in the app stays available.", style = MaterialTheme.typography.bodySmall)
                ErrorBanner(state.errorMessage, onRetry = onRetry)
                if (!state.access.allowed) {
                    Text("Notifications are off. You can keep using all financial features.", style = MaterialTheme.typography.bodyMedium)
                    if (state.access.needsRuntimePermission) OutlinedButton(onClick = onRequestPermission) { Text("Allow notifications") }
                }
                TextButton(onClick = onOpenSystemSettings) { Text("System notification settings") }
                if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val settings = state.settings
                if (settings != null) ReminderType.entries.forEach { type ->
                    val schedule = settings[type]
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(type.title, style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(schedule.enabled, { onEnabled(type, it) }, enabled = !state.isSaving,
                            modifier = Modifier.semantics { contentDescription = type.title })
                    }
                    val time = String.format(Locale.US, "%02d:%02d", schedule.minuteOfDay / 60, schedule.minuteOfDay % 60)
                    TextButton(enabled = !state.isSaving, onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        editingTime = type
                    }) {
                        Text(if (type == ReminderType.DAILY_LOG) "Daily reminder time: $time" else "${type.title}: $time")
                    }
                    if (type == ReminderType.WEEKLY_SUMMARY) Box {
                        TextButton(enabled = !state.isSaving, onClick = { showWeekdays = true }) {
                            Text("Summary day: ${DayOfWeek.of(schedule.dayOfWeek).getDisplayName(TextStyle.FULL, locale)}")
                        }
                        DropdownMenu(showWeekdays, { showWeekdays = false }) {
                            DayOfWeek.entries.forEach { day -> DropdownMenuItem(
                                text = { Text(day.getDisplayName(TextStyle.FULL, locale)) },
                                onClick = { showWeekdays = false; onWeekday(day.value) }) }
                        }
                    }
                }
            }
        }
    }
    editingTime?.let { type ->
        val schedule = state.settings?.get(type) ?: return@let
        key(type, state.settings.userId) {
            // The clock selector avoids the automatic IME/focus transition of TimeInput inside
            // AlertDialog. Explicit AM/PM also prevents saving 04:30 when 16:30 was intended.
            val picker = rememberTimePickerState(schedule.minuteOfDay / 60, schedule.minuteOfDay % 60, is24Hour = false)
            Dialog(onDismissRequest = { editingTime = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.widthIn(max = 384.dp).fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(type.title, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.fillMaxWidth())
                        Text("Sri Lanka time (Asia/Colombo). Select AM or PM.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                        TimePicker(picker, layoutType = TimePickerLayoutType.Vertical,
                            colors = TimePickerDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                periodSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant))
                        if (!schedule.enabled) Text("This reminder is off. Turn on its switch after saving the time.",
                            style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingTime = null }) { Text("Cancel") }
                            TextButton(onClick = {
                                onTime(type, picker.hour, picker.minute)
                                editingTime = null
                            }) { Text("Save time") }
                        }
                    }
                }
            }
        }
    }
}
