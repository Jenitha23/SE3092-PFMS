package com.pfms.app.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.InfoBanner
import com.pfms.app.ui.component.MailVectorIcon
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsLogo
import com.pfms.app.ui.component.PfmsTextField
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.AuthViewModel

/**
 * Password Reset Screen matching the reference fintech styling:
 * - 3-bar rising financial graph logo.
 * - Circular lock/mail badge.
 * - 14dp rounded input field with vector mail icon.
 * - Emerald CTA and back navigation.
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
            Spacer(modifier = Modifier.height(16.dp))

            // ── Top Navigation Bar ─────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateToLogin,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                PfmsLogo()

                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Circular Vector Mail Badge ─────────────────────────────
            Surface(
                modifier = Modifier.size(76.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                tonalElevation = 1.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    MailVectorIcon(
                        modifier = Modifier.size(32.dp),
                        tint = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Title & Description ────────────────────────────────────
            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enter the email linked to your account and we'll send you a password reset link.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Feedback Banners ───────────────────────────────────────
            ErrorBanner(message = uiState.errorMessage)
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            InfoBanner(message = uiState.infoMessage)
            if (uiState.infoMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ── Email Input Field ──────────────────────────────────────
            PfmsTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (uiState.errorMessage != null || uiState.infoMessage != null || uiState.fieldErrors.email != null) {
                        authViewModel.clearMessages()
                    }
                },
                label = "Email address",
                error = uiState.fieldErrors.email,
                enabled = !uiState.isLoading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focusManager.clearFocus()
                    authViewModel.sendPasswordReset(email.trim())
                },
                leadingIcon = {
                    MailVectorIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary Action: Send Reset Link ────────────────────────
            PfmsButton(
                text = "Send Reset Link",
                onClick = {
                    focusManager.clearFocus()
                    authViewModel.sendPasswordReset(email.trim())
                },
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Bottom Helper Link ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remember your password? ",
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

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
