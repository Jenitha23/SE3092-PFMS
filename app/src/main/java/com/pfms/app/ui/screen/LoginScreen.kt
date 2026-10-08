package com.pfms.app.ui.screen

import android.app.Activity
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.GoogleSignInButton
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsPasswordField
import com.pfms.app.ui.component.PfmsTextField
import com.pfms.app.viewmodel.AuthViewModel

/**
 * Professional Material 3 login screen for PFMS.
 *
 * - Driven by [AuthViewModel.uiState] for loading / error / field-error states.
 * - Does **not** navigate to Dashboard on success — [com.pfms.app.viewmodel.SessionViewModel.session]
 *   becoming Authenticated triggers that via the session gate in the NavGraph.
 * - [onNavigateToRegister] and [onNavigateToForgotPassword] are plain callbacks so the
 *   screen stays independent of NavController.
 */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
            Spacer(modifier = Modifier.height(72.dp))

            // ── Branding ───────────────────────────────────────────────
            Text(
                text = "PFMS",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Take control of your finances",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Error banner ───────────────────────────────────────────
            ErrorBanner(message = uiState.errorMessage)
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Email field ────────────────────────────────────────────
            PfmsTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (uiState.errorMessage != null || uiState.fieldErrors.email != null) {
                        authViewModel.clearMessages()
                    }
                },
                label = "Email",
                error = uiState.fieldErrors.email,
                enabled = !uiState.isLoading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                leadingIcon = {
                    Text("✉", style = MaterialTheme.typography.bodyLarge)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Password field ─────────────────────────────────────────
            PfmsPasswordField(
                value = password,
                onValueChange = {
                    password = it
                    if (uiState.errorMessage != null || uiState.fieldErrors.password != null) {
                        authViewModel.clearMessages()
                    }
                },
                label = "Password",
                error = uiState.fieldErrors.password,
                enabled = !uiState.isLoading,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focusManager.clearFocus()
                    authViewModel.login(email.trim(), password)
                },
                leadingIcon = {
                    Text("🔒", style = MaterialTheme.typography.bodyLarge)
                }
            )

            // ── Forgot password link ───────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onNavigateToForgotPassword,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = "Forgot Password?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Login button ───────────────────────────────────────────
            PfmsButton(
                text = "Log In",
                onClick = {
                    focusManager.clearFocus()
                    authViewModel.login(email.trim(), password)
                },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── "or" divider ───────────────────────────────────────────
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

            Spacer(modifier = Modifier.height(24.dp))

            // ── Google sign-in ─────────────────────────────────────────
            GoogleSignInButton(
                onClick = {
                    authViewModel.clearMessages()
                    // Safely obtain the Activity context — required by Credential Manager.
                    val activity = context as? Activity ?: return@GoogleSignInButton
                    authViewModel.signInWithGoogle(activity)
                },
                enabled = !uiState.isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Register link ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onNavigateToRegister,
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
