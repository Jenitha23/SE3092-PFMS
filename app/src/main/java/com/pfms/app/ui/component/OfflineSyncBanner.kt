package com.pfms.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.model.OfflineSyncState

/** Presentation only: connectivity and queue counts come from the ViewModel. */
@Composable
fun OfflineSyncBanner(state: OfflineSyncState, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    if (state.isOnline && state.pendingChanges == 0 && state.errorMessage == null) return
    val pending = "${state.pendingChanges} ${if (state.pendingChanges == 1) "change" else "changes"} waiting to sync"
    Surface(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = if (state.isOnline) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = if (state.isOnline && state.errorMessage != null) {
                (if (state.pendingChanges > 0) "Your changes are saved on this device and are waiting to sync. " else "") + state.errorMessage
            } else if (state.isOnline) "Saved on this device • $pending"
                else if (state.pendingChanges > 0) "Offline • $pending. Your changes are saved on this device."
                else "Offline • Showing saved data. New entries will sync when connected.",
            style = MaterialTheme.typography.bodySmall
        )
        if (state.isOnline && state.canRetry && onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
