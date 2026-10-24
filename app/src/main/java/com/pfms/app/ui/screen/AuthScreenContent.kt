package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pfms.app.domain.validation.PasswordStrength
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.AuthTheme
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.AuthAction
import com.pfms.app.viewmodel.AuthUiState

/** Stateless rendering shared by both routes. Form actions are handled by the ViewModel. */
@Composable
fun AuthScreenContent(
    state: AuthUiState,
    isRegistration: Boolean,
    onAction: (AuthAction) -> Unit,
    onSwitchScreen: () -> Unit,
    onGoogle: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onPrivacyPolicy: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onAction(if (isRegistration) AuthAction.Register else AuthAction.Login)
    }
    AuthTheme {
        AuthBackdrop(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                // Measure the usable window before IME padding. Short windows keep scrolling;
                // both authentication screens share the same spacing on normal phones.
                val compactLayout = maxHeight < 900.dp
                Column(Modifier.fillMaxSize().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(if (compactLayout) 12.dp else 28.dp))
                        AuthHeader(isRegistration, compact = compactLayout)
                        Spacer(Modifier.height(if (compactLayout) 16.dp else 28.dp))
                        AuthTabs(isRegistration, enabled = !state.isLoading,
                            onLogin = if (isRegistration) onSwitchScreen else ({}),
                            onRegister = if (isRegistration) ({}) else onSwitchScreen,
                            compact = compactLayout)
                        Spacer(Modifier.height(if (compactLayout) 16.dp else 24.dp))
                        Surface(shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(if (compactLayout) 16.dp else 20.dp),
                                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 10.dp else 16.dp)) {
                                if (state.errorMessage != null) ErrorBanner(state.errorMessage)
                                if (state.infoMessage != null) InfoBanner(state.infoMessage)
                                if (isRegistration) {
                                    AuthInput(state.displayName, { onAction(AuthAction.NameChanged(it)) },
                                        label = "FULL NAME", placeholder = "e.g. Alex Morgan",
                                        enabled = !state.isLoading, error = state.fieldErrors.displayName,
                                        compact = compactLayout,
                                        keyboardType = KeyboardType.Text,
                                        leadingIcon = { Icon(AuthPersonIcon, contentDescription = null, modifier = Modifier.size(22.dp)) })
                                }
                                AuthInput(state.email, { onAction(AuthAction.EmailChanged(it)) },
                                    label = if (isRegistration) "EMAIL ADDRESS" else "Email Address",
                                    placeholder = if (isRegistration) "alex@example.com" else "name@example.com",
                                    enabled = !state.isLoading, error = state.fieldErrors.email,
                                    compact = compactLayout,
                                    keyboardType = KeyboardType.Email,
                                    leadingIcon = { Icon(AuthMailIcon, contentDescription = null, modifier = Modifier.size(22.dp)) })
                                Column(verticalArrangement = Arrangement.spacedBy(if (compactLayout) 8.dp else 10.dp)) {
                                    AuthInput(state.password, { onAction(AuthAction.PasswordChanged(it)) },
                                        label = if (isRegistration) "CREATE PASSWORD" else "Password",
                                        placeholder = if (isRegistration) "At least 8 characters" else "Enter your password",
                                        enabled = !state.isLoading, error = state.fieldErrors.password,
                                        compact = compactLayout,
                                        keyboardType = KeyboardType.Password, imeAction = ImeAction.Done,
                                        isPassword = true, passwordVisible = state.passwordVisible,
                                        onToggleVisibility = { onAction(AuthAction.TogglePasswordVisibility) },
                                        onDone = submit, leadingIcon = {
                                            Icon(AuthLockIcon, contentDescription = null, modifier = Modifier.size(22.dp))
                                        })
                                    if (isRegistration) AuthPasswordStrength(state.passwordStrength)
                                }
                                if (isRegistration) AuthTerms(state, onAction, onPrivacyPolicy)
                                else LoginOptions(state, onAction, onForgotPassword)
                                AuthPrimaryButton(if (isRegistration) "Create Account" else "Log In",
                                    state.isLoading, submit, compact = compactLayout)
                                if (isRegistration) {
                                    AuthDivider("OR SIGN UP WITH")
                                    AuthGoogleButton(true, !state.isLoading, onGoogle, compact = compactLayout)
                                }
                            }
                        }
                        if (!isRegistration) {
                            Spacer(Modifier.height(if (compactLayout) 16.dp else 26.dp))
                            AuthDivider("OR CONTINUE WITH")
                            Spacer(Modifier.height(if (compactLayout) 16.dp else 26.dp))
                            AuthGoogleButton(false, !state.isLoading, onGoogle, compact = compactLayout)
                        }
                        Spacer(Modifier.height(if (compactLayout) 12.dp else 26.dp))
                        AuthFooter(isRegistration, !state.isLoading, onSwitchScreen, compact = compactLayout)
                        Spacer(Modifier.height(if (compactLayout) 12.dp else 28.dp))
                    }
                }
            }
        }
        if (state.showTermsNotice) {
            AlertDialog(onDismissRequest = { onAction(AuthAction.DismissTerms) },
                title = { Text("Terms of Service") },
                text = { Text("Terms of Service have not been provided for this app yet.") },
                confirmButton = {
                    TextButton(onClick = { onAction(AuthAction.DismissTerms) }) { Text("Close") }
                })
        }
    }
}

@Composable
private fun LoginOptions(state: AuthUiState, onAction: (AuthAction) -> Unit, onForgot: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically) {
        AuthCheckRow(state.rememberMe, !state.isLoading,
            { onAction(AuthAction.RememberMeChanged(it)) }) {
            Text("Remember me", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onForgot, enabled = !state.isLoading,
            contentPadding = PaddingValues(start = 8.dp)) {
            Text("Forgot password?", style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AuthTerms(state: AuthUiState, onAction: (AuthAction) -> Unit, onPrivacy: () -> Unit) {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline))
    Column {
        AuthCheckRow(state.termsAccepted, !state.isLoading,
            { onAction(AuthAction.TermsAcceptedChanged(it)) }) {
            Text(buildAnnotatedString {
                append("I agree to the ")
                withLink(LinkAnnotation.Clickable("terms", linkStyle) {
                    if (!state.isLoading) onAction(AuthAction.ShowTerms)
                }) { append("Terms of Service") }
                append(" and ")
                withLink(LinkAnnotation.Clickable("privacy", linkStyle) {
                    if (!state.isLoading) onPrivacy()
                }) { append("Privacy Policy") }
            }, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.termsError != null) Text(state.termsError, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AuthFooter(isRegistration: Boolean, enabled: Boolean, onSwitch: () -> Unit, compact: Boolean = false) {
    Text(buildAnnotatedString {
        append(if (isRegistration) "Already have an account?  " else "Don't have an account?  ")
        withLink(LinkAnnotation.Clickable("switch", TextLinkStyles(SpanStyle(
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
        ))) { if (enabled) onSwitch() }) {
            append(if (isRegistration) "Log In" else "Sign Up")
        }
    }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("auth_footer"),
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Preview(name = "Login reference", widthDp = 390, heightDp = 825, showBackground = true)
@Composable
private fun LoginPreview() {
    PFMSTheme(darkTheme = false) { AuthScreenContent(AuthUiState(), false, {}, {}, {}) }
}

@Preview(name = "Signup reference", widthDp = 390, heightDp = 865, showBackground = true)
@Composable
private fun RegisterPreview() {
    PFMSTheme(darkTheme = false) { AuthScreenContent(AuthUiState(), true, {}, {}, {}) }
}

@Preview(name = "Signup phone", widthDp = 390, heightDp = 800, showBackground = true)
@Composable
private fun PhoneRegisterPreview() {
    PFMSTheme(darkTheme = false) { AuthScreenContent(AuthUiState(), true, {}, {}, {}) }
}

@Preview(name = "Signup compact large text", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun CompactRegisterPreview() {
    PFMSTheme(darkTheme = false) { AuthScreenContent(AuthUiState(), true, {}, {}, {}) }
}

@Preview(name = "Login compact", widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable
private fun CompactLoginPreview() {
    PFMSTheme(darkTheme = false) { AuthScreenContent(AuthUiState(), false, {}, {}, {}) }
}

@Preview(name = "Signup dark", widthDp = 390, heightDp = 865)
@Composable
private fun DarkRegisterPreview() {
    PFMSTheme(darkTheme = true) {
        AuthScreenContent(AuthUiState(passwordStrength = PasswordStrength.STRONG), true, {}, {}, {})
    }
}
