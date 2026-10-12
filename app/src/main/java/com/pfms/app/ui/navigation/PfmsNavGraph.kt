package com.pfms.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pfms.app.domain.model.SessionState
import com.pfms.app.ui.screen.DashboardScreen
import com.pfms.app.ui.screen.ForgotPasswordScreen
import com.pfms.app.ui.screen.LockScreen
import com.pfms.app.ui.screen.LoginScreen
import com.pfms.app.ui.screen.RegisterScreen
import com.pfms.app.ui.screen.SettingsScreen
import com.pfms.app.ui.screen.SplashScreen
import com.pfms.app.viewmodel.AppLockState
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.ui.screen.income.IncomeFormScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.pfms.app.ui.screen.income.IncomeListScreen

/**
 * Root composable that hosts the navigation graph and the session gate.
 *
 * Navigation is driven entirely by [SessionViewModel.session] and [SessionViewModel.lockState].
 * No screen ever navigates to Dashboard directly — it only appears when the session becomes
 * [SessionState.Authenticated] **and** the lock state is [AppLockState.Unlocked].
 */
@Composable
fun PfmsNavGraph(
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val lockState by sessionViewModel.lockState.collectAsStateWithLifecycle()

    // ── TASK 6: Lifecycle-aware lock ────────────────────────────────────
    // Track whether we have ever been to the RESUMED state so that the
    // very first composition (cold start) does not trigger a lock.
    // Also skip locking right after a fresh login by gating on the
    // session already being Authenticated when we went to background.
    var hasResumedOnce by rememberSaveable { mutableStateOf(false) }
    var wasAuthenticatedOnPause by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (hasResumedOnce && wasAuthenticatedOnPause) {
                        // Returning from background — let SessionViewModel decide
                        // whether biometrics are enabled and should lock.
                        sessionViewModel.lockApp()
                    }
                    hasResumedOnce = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    wasAuthenticatedOnPause = session is SessionState.Authenticated
                }
                else -> { /* no-op */ }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Session gate → target route ────────────────────────────────────
    val targetRoute = when (session) {
        SessionState.Loading -> Routes.Splash
        SessionState.Unauthenticated -> Routes.Login
        is SessionState.Authenticated -> when (lockState) {
            AppLockState.Checking -> Routes.Splash
            AppLockState.Locked   -> Routes.Lock
            AppLockState.Unlocked -> Routes.Dashboard
        }
    }

    // Navigate reactively. popUpTo(0) clears the back-stack so the user
    // cannot press Back into a screen they no longer belong on.
    LaunchedEffect(targetRoute) {
        val current = navController.currentDestination?.route
        if (current != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // ── NavHost ────────────────────────────────────────────────────────
    NavHost(
        navController = navController,
        startDestination = Routes.Splash
    ) {
        composable(Routes.Splash)          { SplashScreen() }
        composable(Routes.Login) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Routes.Register) { launchSingleTop = true }
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Routes.ForgotPassword) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.Register) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack(Routes.Login, inclusive = false)
                }
            )
        }
        composable(Routes.ForgotPassword) {
            ForgotPasswordScreen(
                onNavigateToLogin = {
                    navController.popBackStack(Routes.Login, inclusive = false)
                }
            )
        }
        composable(Routes.Lock) {
            LockScreen(sessionViewModel = sessionViewModel)
        }

        composable(Routes.Dashboard) {
            DashboardScreen(
                sessionViewModel = sessionViewModel,
                onNavigateToSettings = {
                    navController.navigate(Routes.Settings) {
                        launchSingleTop = true
                    }
                },
                onNavigateToIncome = {
                    navController.navigate(Routes.IncomeList) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                sessionViewModel = sessionViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }


        composable(Routes.IncomeList) {
            var showFormMessage by remember {
                mutableStateOf(false)
            }

            IncomeListScreen(
                incomes = emptyList(),
                onAddIncome = {
                    navController.navigate(Routes.AddIncome) {
                        launchSingleTop = true
                    }
                },
                onEditIncome = { incomeId ->
                    navController.navigate(
                        Routes.editIncome(incomeId)
                    )
                },
                onDeleteIncome = { _ ->
                    // Connected to ViewModel in the CRUD step.
                },
                onBack = {
                    navController.popBackStack()
                }
            )

            if (showFormMessage) {
                AlertDialog(
                    onDismissRequest = {
                        showFormMessage = false
                    },
                    title = {
                        Text("Add Income")
                    },
                    text = {
                        Text(
                            "The income entry form will be " +
                                    "implemented in the next step."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showFormMessage = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }


        composable(Routes.AddIncome) {

            var showPendingMessage by remember {
                mutableStateOf(false)
            }

            IncomeFormScreen(
                onBack = {
                    navController.popBackStack()
                },
                onSave = { input ->
                    // Temporary UI integration.
                    // Input will be passed to IncomeViewModel
                    // when repository persistence is connected.
                    showPendingMessage = true
                }
            )

            if (showPendingMessage) {
                AlertDialog(
                    onDismissRequest = {
                        showPendingMessage = false
                    },
                    title = {
                        Text("Form Validation Successful")
                    },
                    text = {
                        Text(
                            "Your income details are valid. " +
                                    "Saving records will be enabled " +
                                    "after the Income ViewModel and " +
                                    "repository are connected."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showPendingMessage = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                )
            }
        }


    }
}
