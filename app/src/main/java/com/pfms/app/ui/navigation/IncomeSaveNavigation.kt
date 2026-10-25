package com.pfms.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.pfms.app.viewmodel.IncomeViewModel

/** Single graph-level owner for acknowledged save completion; no replay after recomposition. */
@Composable
internal fun IncomeSaveNavigationEffect(navController: NavHostController, viewModel: IncomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saveSuccessful, navController, viewModel) {
        // Acknowledge the live state, not the potentially stale snapshot held by a collector.
        // No suspension between consuming the completion and navigating.
        if (state.saveSuccessful && viewModel.consumeSaveSuccess()) {
            when (navController.currentDestination?.route) {
                Routes.AddIncome, Routes.EditIncome -> {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.IncomeList) { launchSingleTop = true }
                    }
                }
                // A late save must not pop a screen the user has since opened (or the lock screen).
            }
        }
    }
}
