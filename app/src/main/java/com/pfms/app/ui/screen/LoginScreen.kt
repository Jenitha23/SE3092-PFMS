package com.pfms.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.AuthViewModel

/** Route adapter: lifecycle state and Android sign-in context stay outside the content. */
@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit = {},
    onNavigateToForgotPassword: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AuthScreenContent(
        state = state,
        isRegistration = false,
        onAction = authViewModel::onAction,
        onSwitchScreen = onNavigateToRegister,
        onForgotPassword = onNavigateToForgotPassword,
        onGoogle = { authViewModel.signInWithGoogle(context) }
    )
}
