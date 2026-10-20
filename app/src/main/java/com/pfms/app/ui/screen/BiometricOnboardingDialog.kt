package com.pfms.app.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Optional, one-time invitation after the first successful login and onboarding. */
@Composable
fun BiometricOnboardingDialog(
    onEnable: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Unlock PFMS faster?") },
        text = {
            Text(
                "Use your enrolled fingerprint or face to unlock PFMS when you return to the app. " +
                        "You can change this anytime in Settings."
            )
        },
        confirmButton = {
            TextButton(onClick = onEnable) { Text("Enable") }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text("Not Now") }
        }
    )
}
