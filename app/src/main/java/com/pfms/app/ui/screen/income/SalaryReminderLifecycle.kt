
package com.pfms.app.ui.screen.income

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun SalaryReminderLifecycle(
    onResume: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentOnResume =
        rememberUpdatedState(onResume)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnResume.value()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        // Handle a screen already in the resumed state.
        if (
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(Lifecycle.State.RESUMED)
        ) {
            currentOnResume.value()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
