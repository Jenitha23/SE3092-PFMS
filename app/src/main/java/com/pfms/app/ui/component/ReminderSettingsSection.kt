package com.pfms.app.ui.component

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
        onRequestPermission = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
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
    var editingTime by remember(state.settings?.userId) { mutableStateOf<ReminderType?>(null) }
    var showWeekdays by remember(state.settings?.userId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Financial Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        ElevatedCard(shape = RoundedCornerShape(16.dp)) {
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
                    TextButton(enabled = !state.isSaving, onClick = { editingTime = type }) {
                        Text(if (type == ReminderType.DAILY_LOG) "Daily reminder time: $time" else "${type.title}: $time")
                    }
                    if (type == ReminderType.WEEKLY_SUMMARY) Box {
                        TextButton(enabled = !state.isSaving, onClick = { showWeekdays = true }) {
                            Text("Summary day: ${DayOfWeek.of(schedule.dayOfWeek).getDisplayName(TextStyle.FULL, Locale.getDefault())}")
                        }
                        DropdownMenu(showWeekdays, { showWeekdays = false }) {
                            DayOfWeek.entries.forEach { day -> DropdownMenuItem(
                                text = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
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
            val picker = rememberTimePickerState(schedule.minuteOfDay / 60, schedule.minuteOfDay % 60, is24Hour = true)
            AlertDialog(onDismissRequest = { editingTime = null }, title = { Text(type.title) }, text = { TimeInput(picker) },
                confirmButton = { TextButton(onClick = { onTime(type, picker.hour, picker.minute); editingTime = null }) { Text("Save time") } },
                dismissButton = { TextButton(onClick = { editingTime = null }) { Text("Cancel") } })
        }
    }
}
