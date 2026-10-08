package com.pfms.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.GoogleSignInButton
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsPasswordField
import com.pfms.app.viewmodel.SessionViewModel

/**
 * Material 3 Biometric App Lock screen for PFMS (FR-04).
 *
 * - Integrated with [SessionViewModel.lockUiState] and [SessionViewModel.lockState].
 * - Primary unlock is via biometric prompt ([SessionViewModel.unlockWithBiometric]).
 * - Automatically prompts for biometrics once when the screen appears in the Locked state.
 * - When biometrics are dismissed or fail, [SessionViewModel.lockUiState.showFallback] is set,
 *   revealing the password and Google re-authentication fallbacks.
 * - Does NOT navigate directly to Dashboard. Unlock success transitions [SessionViewModel.lockState]
 *   to Unlocked, which the root session gate handles automatically.
 */
@Composable
fun LockScreen(
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val lockUiState by sessionViewModel.lockUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var password by rememberSaveable { mutableStateOf("") }
    var hasAutoPrompted by rememberSaveable { mutableStateOf(false) }

    // ── Auto-prompt biometric once on entry ────────────────────────────
    LaunchedEffect(context) {
        val activity = context as? FragmentActivity
        if (activity != null && !hasAutoPrompted && !lockUiState.showFallback) {
            hasAutoPrompted = true
            sessionViewModel.unlockWithBiometric(activity)
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // ── Security Icon Badge ────────────────────────────────────
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "🔒",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Branding & Header ──────────────────────────────────────
            Text(
                text = "PFMS",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Unlock PFMS",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Authenticate to access your financial information.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Error / Status Message Banner ──────────────────────────
            ErrorBanner(message = lockUiState.message)
            if (lockUiState.message != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Primary Action: Biometric Unlock ───────────────────────
            PfmsButton(
                text = "Unlock with Biometrics",
                onClick = {
                    val activity = context as? FragmentActivity ?: return@PfmsButton
                    sessionViewModel.unlockWithBiometric(activity)
                },
                enabled = !lockUiState.isLoading,
                isLoading = lockUiState.isLoading && !lockUiState.showFallback
            )

            // ── Fallback Area: Password & Google ───────────────────────
            AnimatedVisibility(
                visible = lockUiState.showFallback,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "  or use password / Google  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    PfmsPasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Account Password",
                        enabled = !lockUiState.isLoading,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            if (password.isNotBlank()) {
                                focusManager.clearFocus()
                                sessionViewModel.unlockWithPassword(password)
                            }
                        },
                        leadingIcon = {
                            Text("🔑", style = MaterialTheme.typography.bodyLarge)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PfmsButton(
                        text = "Unlock with Password",
                        onClick = {
                            focusManager.clearFocus()
                            sessionViewModel.unlockWithPassword(password)
                        },
                        enabled = !lockUiState.isLoading && password.isNotBlank(),
                        isLoading = lockUiState.isLoading
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Text(
                            text = "  or  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    GoogleSignInButton(
                        onClick = {
                            sessionViewModel.unlockWithGoogle(context)
                        },
                        enabled = !lockUiState.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
