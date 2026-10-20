package com.pfms.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import com.pfms.app.viewmodel.SyncStatusViewModel
import com.pfms.app.ui.component.OfflineSyncBanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.navigation.PfmsNavGraph
import com.pfms.app.ui.navigation.Routes
import com.pfms.app.data.sync.ExpenseReminderWorker
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

// @AndroidEntryPoint is required for hiltViewModel(); FragmentActivity (a ComponentActivity
// subclass) is required by BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()
    private val syncStatusViewModel: SyncStatusViewModel by viewModels()
    private var pendingReminderRoute by mutableStateOf<String?>(null)

    private fun readReminderIntent(intent: Intent?) {
        pendingReminderRoute = Routes.reminderDestination(
            intent?.getStringExtra(ExpenseReminderWorker.EXTRA_DESTINATION_ROUTE)
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReminderIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readReminderIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val syncState by syncStatusViewModel.uiState.collectAsStateWithLifecycle()
            PFMSTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(Modifier.fillMaxSize()) {
                        OfflineSyncBanner(syncState, onRetry = syncStatusViewModel::retry)
                        val contentModifier = Modifier.weight(1f)
                        Box(if (!syncState.isOnline || syncState.pendingChanges > 0 || syncState.errorMessage != null)
                            contentModifier.consumeWindowInsets(WindowInsets.statusBars) else contentModifier) {
                            PfmsNavGraph(
                                pendingReminderRoute = pendingReminderRoute,
                                onReminderConsumed = {
                                    pendingReminderRoute = null
                                    intent?.removeExtra(ExpenseReminderWorker.EXTRA_DESTINATION_ROUTE)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
