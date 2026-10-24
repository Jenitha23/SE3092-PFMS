package com.pfms.app.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    onNavigateToPrivacyPolicy: () -> Unit = {}
) {
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AuthScreenContent(
        state = state,
        isRegistration = true,
        onAction = authViewModel::onAction,
        onSwitchScreen = onNavigateToLogin,
        onGoogle = { authViewModel.submitGoogleRegistration(context) },
        onPrivacyPolicy = onNavigateToPrivacyPolicy
    )
}
