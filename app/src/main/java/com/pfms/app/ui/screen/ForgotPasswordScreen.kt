package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.InfoBanner
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsTextField
import com.pfms.app.viewmodel.AuthViewModel

/**
 * Material 3 password reset request screen for PFMS.
 *
 * - Driven by [AuthViewModel.uiState] for loading, error, infoMessage, and email field error.
 * - When [AuthViewModel.uiState.infoMessage] is shown, the screen remains visible so the user
 *   can read the success message and navigate back to Login at their own pace.
 * - [onNavigateToLogin] callback used for returning to Login destination.
 */
@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }

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

            // ── Branding & Title ───────────────────────────────────────
            Text(
                text = "PFMS",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Forgot Password?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your email address and we'll send you a password reset link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Feedback Banners ───────────────────────────────────────
            ErrorBanner(message = uiState.errorMessage)
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            InfoBanner(message = uiState.infoMessage)
            if (uiState.infoMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Email Field ────────────────────────────────────────────
            PfmsTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (uiState.errorMessage != null || uiState.infoMessage != null || uiState.fieldErrors.email != null) {
                        authViewModel.clearMessages()
                    }
                },
                label = "Email",
                error = uiState.fieldErrors.email,
                enabled = !uiState.isLoading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focusManager.clearFocus()
                    authViewModel.sendPasswordReset(email.trim())
                },
                leadingIcon = {
                    Text("✉", style = MaterialTheme.typography.bodyLarge)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Send Reset Link Button ─────────────────────────────────
            PfmsButton(
                text = "Send Reset Link",
                onClick = {
                    focusManager.clearFocus()
                    authViewModel.sendPasswordReset(email.trim())
                },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Back to Login Link ─────────────────────────────────────
            TextButton(
                onClick = onNavigateToLogin,
                enabled = !uiState.isLoading,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = "← Back to Login",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
