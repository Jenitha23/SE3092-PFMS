package com.pfms.app

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.pfms.app.domain.model.OfflineSyncState
import com.pfms.app.ui.component.OfflineSyncBanner
import com.pfms.app.ui.theme.PFMSTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class OfflineSyncBannerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun indicatorTracksOfflinePendingReconnectedAndFullySyncedStates() {
        val state = MutableStateFlow(OfflineSyncState())
        compose.setContent { PFMSTheme {
            val current by state.collectAsState()
            OfflineSyncBanner(current)
        } }
        compose.onNodeWithText("Offline", substring = true).assertDoesNotExist()
        compose.runOnIdle { state.value = OfflineSyncState(isOnline = false) }
        compose.onNodeWithText("Offline • Showing saved data. New entries will sync when connected.").assertIsDisplayed()
        compose.runOnIdle { state.value = OfflineSyncState(false, 20) }
        compose.onNodeWithText("Offline • 20 changes waiting to sync. Your changes are saved on this device.").assertIsDisplayed()
        compose.runOnIdle { state.value = OfflineSyncState(true, 20) }
        compose.onNodeWithText("Saved on this device • 20 changes waiting to sync").assertIsDisplayed()
        compose.runOnIdle { state.value = OfflineSyncState(true, 0) }
        compose.onNodeWithText("waiting to sync", substring = true).assertDoesNotExist()
    }
}
