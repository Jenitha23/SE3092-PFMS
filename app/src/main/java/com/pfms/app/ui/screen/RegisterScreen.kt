package com.pfms.app.ui.screen

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.AuthTab
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.GoogleSignInButton
import com.pfms.app.ui.component.LockVectorIcon
import com.pfms.app.ui.component.MailVectorIcon
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsLogo
import com.pfms.app.ui.component.PfmsPasswordField
import com.pfms.app.ui.component.PfmsSegmentedAuthTab
import com.pfms.app.ui.component.PfmsTextField
import com.pfms.app.ui.component.UserVectorIcon
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.AuthViewModel

/**
 * Registration Screen matching Reference UI 1:
 * - 3-bar rising financial graph logo.
 * - Segmented "Log In / Sign Up" tab control with active Sign Up indicator.
 * - Form fields with 14dp rounded corners and vector icons.
 * - Full-width emerald "Sign Up" button.
 * - Divider and Google sign-in.
 * - Bottom login navigation.
 */
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var displayName by rememberSaveable { mutableStateOf("") }
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
            Spacer(modifier = Modifier.height(32.dp))

            // ── Top Logo: 3-bar rising chart ───────────────────────────
            PfmsLogo()

            Spacer(modifier = Modifier.height(10.dp))

            // ── App Title ──────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PF",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "MS",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = EmeraldPrimary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // ── Subtitle / Tagline ─────────────────────────────────────
            Text(
                text = "Personal Finance Management",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Better decisions. A brighter future.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Segmented "Log In / Sign Up" Tab ───────────────────────
            PfmsSegmentedAuthTab(
                selectedTab = AuthTab.SIGN_UP,
                onTabSelected = { tab ->
                    if (tab == AuthTab.LOGIN) {
                        onNavigateToLogin()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Error Banner ───────────────────────────────────────────
            ErrorBanner(message = uiState.errorMessage)
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // ── Display Name Field ─────────────────────────────────────
            PfmsTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    if (uiState.errorMessage != null || uiState.fieldErrors.displayName != null) {
                        authViewModel.clearMessages()
                    }
                },
                label = "Display name",
                error = uiState.fieldErrors.displayName,
                enabled = !uiState.isLoading,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                leadingIcon = {
                    UserVectorIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Email Field ────────────────────────────────────────────
            PfmsTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (uiState.errorMessage != null || uiState.fieldErrors.email != null) {
                        authViewModel.clearMessages()
                    }
                },
                label = "Email address",
                error = uiState.fieldErrors.email,
                enabled = !uiState.isLoading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                leadingIcon = {
                    MailVectorIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // ── Password Field ─────────────────────────────────────────
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
                    authViewModel.register(displayName.trim(), email.trim(), password)
                },
                leadingIcon = {
                    LockVectorIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Sign Up Button ─────────────────────────────────────────
            PfmsButton(
                text = "Sign Up",
                onClick = {
                    focusManager.clearFocus()
                    authViewModel.register(displayName.trim(), email.trim(), password)
                },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── "or" Divider ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "  or  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Continue with Google ───────────────────────────────────
            GoogleSignInButton(
                onClick = {
                    authViewModel.clearMessages()
                    val activity = context as? Activity ?: return@GoogleSignInButton
                    authViewModel.signInWithGoogle(activity)
                },
                enabled = !uiState.isLoading
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Bottom: "Already have an account? Log In" ──────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already have an account? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Log In",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = EmeraldPrimary,
                    modifier = Modifier
                        .clickable(enabled = !uiState.isLoading) {
                            onNavigateToLogin()
                        }
                        .padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
