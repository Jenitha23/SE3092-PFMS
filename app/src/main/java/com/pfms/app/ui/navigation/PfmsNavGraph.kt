
package com.pfms.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
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

import com.pfms.app.ui.screen.income.IncomeFormScreen
import com.pfms.app.ui.screen.income.IncomeListScreen
import com.pfms.app.ui.screen.income.SalaryTemplateScreen

import com.pfms.app.viewmodel.AppLockState
import com.pfms.app.viewmodel.SessionViewModel

/**
 * Root navigation graph for PFMS.
 *
 * Handles:
 * - Authentication navigation
 * - Session management
 * - App lock
 * - Dashboard
 * - Settings
 * - Income Management
 * - Recurring Salary
 */
@Composable
fun PfmsNavGraph(
    sessionViewModel: SessionViewModel = hiltViewModel()
) {

    val navController = rememberNavController()

    val session by sessionViewModel.session
        .collectAsStateWithLifecycle()

    val lockState by sessionViewModel.lockState
        .collectAsStateWithLifecycle()

    // -----------------------------------------
    // LIFECYCLE-AWARE APP LOCK
    // -----------------------------------------

    var hasResumedOnce by rememberSaveable {
        mutableStateOf(false)
    }

    var wasAuthenticatedOnPause by rememberSaveable {
        mutableStateOf(false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            when (event) {

                Lifecycle.Event.ON_RESUME -> {

                    if (
                        hasResumedOnce &&
                        wasAuthenticatedOnPause
                    ) {
                        sessionViewModel.lockApp()
                    }

                    hasResumedOnce = true
                }

                Lifecycle.Event.ON_PAUSE -> {

                    wasAuthenticatedOnPause =
                        session is SessionState.Authenticated
                }

                else -> {
                    // No operation
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // -----------------------------------------
    // SESSION GATE
    // -----------------------------------------

    val targetRoute = when (session) {

        SessionState.Loading -> Routes.Splash

        SessionState.Unauthenticated -> Routes.Login

        is SessionState.Authenticated -> {

            when (lockState) {

                AppLockState.Checking -> Routes.Splash

                AppLockState.Locked -> Routes.Lock

                AppLockState.Unlocked -> Routes.Dashboard
            }
        }
    }

    LaunchedEffect(targetRoute) {

        val current =
            navController.currentDestination?.route

        if (current != targetRoute) {

            navController.navigate(targetRoute) {

                popUpTo(0) {
                    inclusive = true
                }

                launchSingleTop = true
            }
        }
    }

    // -----------------------------------------
    // NAVIGATION HOST
    // -----------------------------------------

    NavHost(
        navController = navController,
        startDestination = Routes.Splash
    ) {

        // -------------------------------------
        // SPLASH
        // -------------------------------------

        composable(Routes.Splash) {
            SplashScreen()
        }

        // -------------------------------------
        // LOGIN
        // -------------------------------------

        composable(Routes.Login) {

            LoginScreen(

                onNavigateToRegister = {
                    navController.navigate(
                        Routes.Register
                    ) {
                        launchSingleTop = true
                    }
                },

                onNavigateToForgotPassword = {
                    navController.navigate(
                        Routes.ForgotPassword
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // -------------------------------------
        // REGISTER
        // -------------------------------------

        composable(Routes.Register) {

            RegisterScreen(

                onNavigateToLogin = {
                    navController.popBackStack(
                        Routes.Login,
                        inclusive = false
                    )
                }
            )
        }

        // -------------------------------------
        // FORGOT PASSWORD
        // -------------------------------------

        composable(Routes.ForgotPassword) {

            ForgotPasswordScreen(

                onNavigateToLogin = {
                    navController.popBackStack(
                        Routes.Login,
                        inclusive = false
                    )
                }
            )
        }

        // -------------------------------------
        // APP LOCK
        // -------------------------------------

        composable(Routes.Lock) {

            LockScreen(
                sessionViewModel = sessionViewModel
            )
        }

        // -------------------------------------
        // DASHBOARD
        // -------------------------------------

        composable(Routes.Dashboard) {

            DashboardScreen(

                sessionViewModel = sessionViewModel,

                onNavigateToSettings = {

                    navController.navigate(
                        Routes.Settings
                    ) {
                        launchSingleTop = true
                    }
                },

                onNavigateToIncome = {

                    navController.navigate(
                        Routes.IncomeList
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // -------------------------------------
        // SETTINGS
        // -------------------------------------

        composable(Routes.Settings) {

            SettingsScreen(

                sessionViewModel = sessionViewModel,

                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // =====================================
        // MEMBER 1 - INCOME MANAGEMENT
        // =====================================

        // -------------------------------------
        // INCOME LIST
        // -------------------------------------

        composable(Routes.IncomeList) {

            IncomeListScreen(

                // Temporary empty list until
                // Member 3 implements repository.
                incomes = emptyList(),

                onAddIncome = {

                    navController.navigate(
                        Routes.AddIncome
                    ) {
                        launchSingleTop = true
                    }
                },

                onEditIncome = { incomeId ->

                    navController.navigate(
                        Routes.editIncome(incomeId)
                    ) {
                        launchSingleTop = true
                    }
                },

                onDeleteIncome = { _ ->

                    // Future integration:
                    // IncomeViewModel.deleteIncome()
                    //
                    // Requires repository implementation.
                },

                onBack = {
                    navController.popBackStack()
                },

                onRecurringSalary = {

                    navController.navigate(
                        Routes.SalaryTemplate
                    ) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // -------------------------------------
        // ADD INCOME
        // -------------------------------------

        composable(Routes.AddIncome) {

            var showPendingMessage by remember {
                mutableStateOf(false)
            }

            IncomeFormScreen(

                onBack = {
                    navController.popBackStack()
                },

                onSave = { _ ->

                    // Temporary integration.
                    // No income record is persisted.
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
                                    "Saving will be enabled when " +
                                    "the IncomeRepository is connected. " +
                                    "No income record has been saved."
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

        // -------------------------------------
        // EDIT INCOME - STEP 11
        // -------------------------------------

        composable(
            route = Routes.EditIncome,

            arguments = listOf(
                navArgument("incomeId") {
                    type = NavType.StringType
                }
            )

        ) { backStackEntry ->

            val incomeId =
                backStackEntry.arguments
                    ?.getString("incomeId")

            // Member 3 has not implemented
            // getIncomeById() yet.
            //
            // We must not display a blank form
            // and accidentally overwrite an entry.

            Scaffold(

                topBar = {

                    TextButton(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Text("Back")
                    }
                }

            ) { paddingValues ->

                Column(

                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),

                    verticalArrangement =
                        Arrangement.Center,

                    horizontalAlignment =
                        Alignment.CenterHorizontally

                ) {

                    Text(
                        text = "Edit Income",
                        style = MaterialTheme
                            .typography.headlineSmall
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Text(
                        text = if (incomeId.isNullOrBlank()) {
                            "Invalid income record."
                        } else {
                            "Income editing will be " +
                                    "available once the " +
                                    "repository is connected."
                        },
                        style = MaterialTheme
                            .typography.bodyMedium
                    )

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            navController.popBackStack()
                        }
                    ) {
                        Text("Return to Income")
                    }
                }
            }
        }

        // -------------------------------------
        // RECURRING SALARY TEMPLATE
        // -------------------------------------

        composable(Routes.SalaryTemplate) {

            var showPendingMessage by remember {
                mutableStateOf(false)
            }

            SalaryTemplateScreen(

                initialTemplate = null,

                onBack = {
                    navController.popBackStack()
                },

                onSaveTemplate = { _ ->

                    // Member 3 will implement
                    // salary template persistence.
                    showPendingMessage = true
                }
            )

            if (showPendingMessage) {

                AlertDialog(

                    onDismissRequest = {
                        showPendingMessage = false
                    },

                    title = {
                        Text("Storage Integration Pending")
                    },

                    text = {
                        Text(
                            "The salary template form is ready, " +
                                    "but saving is not available yet. " +
                                    "Room and Firebase integration " +
                                    "must be completed first. " +
                                    "No salary template has been saved."
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
