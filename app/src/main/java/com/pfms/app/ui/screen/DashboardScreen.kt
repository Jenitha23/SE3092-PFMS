package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.domain.model.LogoutResult
import com.pfms.app.domain.model.SessionState
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.viewmodel.SettingsViewModel

/**
 * Authenticated landing screen for PFMS.
 *
 * Serves as the landing hub following successful authentication / unlock:
 * - Shows current authenticated user & email.
 * - Quick action to navigate to [SettingsScreen] via callback.
 * - Safe logout trigger integrated with [SessionViewModel.logout].
 * - Non-functional preview cards indicating forthcoming finance modules
 *   without fake calculations or unauthorized data queries.
 */
@Composable
fun DashboardScreen(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val logoutResult by sessionViewModel.logoutResult.collectAsStateWithLifecycle()

    val authUser = (session as? SessionState.Authenticated)?.user
    val displayName = profile?.displayName?.ifBlank { null }
        ?: authUser?.displayName?.ifBlank { null }
        ?: "User"
    val email = authUser?.email
        ?: profile?.email
        ?: "No email available"

    val logoutError = (logoutResult as? LogoutResult.Failure)?.message

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
            // ── Top Bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PFMS",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = "⚙",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Logout Error Banner (if any) ───────────────────────────
            ErrorBanner(message = logoutError)
            if (logoutError != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Greeting & Account Overview ────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Welcome back,",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Authenticated Status Indicator
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Authenticated & Secure",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Quick Action: Profile & Settings ───────────────────────
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Profile & Security",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Manage account, password, biometrics, and payment method",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    PfmsButton(
                        text = "Open Settings",
                        onClick = onNavigateToSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Financial Modules Preview Section ──────────────────────
            Text(
                text = "Financial Modules",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Preview Card 1: Monthly Overview
            FinancePreviewCard(
                icon = "📊",
                title = "Monthly Overview",
                description = "Track income, expenditure, and category summaries across all accounts.",
                status = "Available in the finance module"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Preview Card 2: Savings Goal
            FinancePreviewCard(
                icon = "🎯",
                title = "Savings Goals",
                description = "Set targets, manage monthly contributions, and track progress.",
                status = "Coming soon"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Preview Card 3: Recent Activity
            FinancePreviewCard(
                icon = "🕒",
                title = "Recent Activity",
                description = "Detailed records of income and expense transactions.",
                status = "Coming soon"
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Logout Action ──────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    sessionViewModel.logout(force = false)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Log Out",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Unsynchronized Data Warning Dialog ─────────────────────────────
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
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
}

/**
 * Reusable visual placeholder card for forthcoming financial modules.
 * Strictly non-functional: displays no fake balances or calculated values.
 */
@Composable
private fun FinancePreviewCard(
    icon: String,
    title: String,
    description: String,
    status: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = icon, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
