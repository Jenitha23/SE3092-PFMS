package com.pfms.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/** Ignore a second tap delivered by an outgoing screen after the first tap changed destinations. */
internal fun NavHostController.openFinanceFeature(origin: NavBackStackEntry, route: String) {
    if (currentBackStackEntry?.id != origin.id) return
    navigate(route) { launchSingleTop = true }
}

internal fun NavHostController.closeSpendingPatterns(origin: NavBackStackEntry) {
    if (currentBackStackEntry?.id != origin.id) return
    if (previousBackStackEntry != null && popBackStack()) return
    navigate(Routes.SpendingAnalytics) {
        popUpTo(Routes.SpendingPatterns) { inclusive = true }
        launchSingleTop = true
    }
}

/** Simulator Back and its missing-goal action have the same explicit Savings Goal destination. */
internal fun NavHostController.returnToSavingsGoalFromSimulator(origin: NavBackStackEntry) {
    if (currentBackStackEntry?.id != origin.id) return
    if (popBackStack(Routes.SavingsGoal, inclusive = false)) return
    navigate(Routes.SavingsGoal) {
        popUpTo(Routes.SavingsSimulator) { inclusive = true }
        launchSingleTop = true
    }
}

/** Give Android Back the same behavior as the destination's toolbar Back. */
@Composable
internal fun FinanceFeatureBackHandler(navController: NavHostController, entry: NavBackStackEntry, onBack: () -> Unit) {
    val current by navController.currentBackStackEntryAsState()
    BackHandler(enabled = current?.id == entry.id, onBack = onBack)
}
