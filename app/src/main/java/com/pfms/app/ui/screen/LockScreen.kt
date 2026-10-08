package com.pfms.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.ErrorBanner
import com.pfms.app.ui.component.GoogleSignInButton
import com.pfms.app.ui.component.LockVectorIcon
import com.pfms.app.ui.component.PfmsButton
import com.pfms.app.ui.component.PfmsLogo
import com.pfms.app.ui.component.PfmsPasswordField
import com.pfms.app.ui.theme.EmeraldPrimary
import com.pfms.app.viewmodel.SessionViewModel

/**
 * Biometric App Lock Screen matching the reference fintech design:
 * - 3-bar logo header.
 * - Circular vector lock badge.
 * - Auto-prompt and manual biometric unlock.
 * - Smooth fallback card with vector lock password field and Google sign-in.
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
            Spacer(modifier = Modifier.height(36.dp))

            // ── Top Brand Header ──────────────────────────────────────
            PfmsLogo()

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PF",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "MS",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = EmeraldPrimary
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Circular Vector Lock Badge ─────────────────────────────
            Surface(
                modifier = Modifier.size(84.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    LockVectorIcon(
                        modifier = Modifier.size(36.dp),
                        tint = EmeraldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Title & Subtitle ──────────────────────────────────────
            Text(
                text = "Unlock PFMS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Authenticate to continue securely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Locked Status Pill ─────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(EmeraldPrimary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Locked",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Error Banner ───────────────────────────────────────────
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
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "  or use another method  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PfmsPasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Password",
                                enabled = !lockUiState.isLoading,
                                imeAction = ImeAction.Done,
                                onImeAction = {
                                    if (password.isNotBlank()) {
                                        focusManager.clearFocus()
                                        sessionViewModel.unlockWithPassword(password)
                                    }
                                },
                                leadingIcon = {
                                    LockVectorIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

                            Spacer(modifier = Modifier.height(18.dp))

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

                            GoogleSignInButton(
                                onClick = {
                                    sessionViewModel.unlockWithGoogle(context)
                                },
                                enabled = !lockUiState.isLoading
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
