package com.pfms.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.component.*
import com.pfms.app.ui.theme.AuthTheme
import com.pfms.app.viewmodel.AuthViewModel

@Composable
fun ForgotPasswordScreen(onNavigateToLogin: () -> Unit = {}, authViewModel: AuthViewModel = hiltViewModel()) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    val submit = { focus.clearFocus(); authViewModel.sendPasswordReset(email.trim()) }
    AuthTheme {
        AuthBackdrop(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    IconButton(onClick = onNavigateToLogin, enabled = !state.isLoading) { FinanceIcon(FinanceSymbol.BACK, description = "Back") }
                    FinanceBrand()
                    Text("Forgot password?", style = MaterialTheme.typography.headlineSmall)
                    Text("Enter the email linked to your account and we'll send you a password reset link.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ErrorBanner(state.errorMessage)
                            InfoBanner(state.infoMessage)
                            PfmsTextField(email, {
                                email = it
                                authViewModel.clearMessages()
                            }, label = "Email address", error = state.fieldErrors.email, enabled = !state.isLoading,
                                keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, onImeAction = submit,
                                leadingIcon = { FinanceIcon(FinanceSymbol.MAIL, tint = MaterialTheme.colorScheme.onSurfaceVariant) })
                            PfmsButton("Send Reset Link", submit, isLoading = state.isLoading)
                        }
                    }
                    TextButton(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth(), enabled = !state.isLoading) { Text("Back to Log In") }
                }
            }
        }
    }
}
