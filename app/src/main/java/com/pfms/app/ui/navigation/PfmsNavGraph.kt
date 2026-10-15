
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

import com.pfms.app.ui.screen.income.IncomeAnalyticsScreen
import com.pfms.app.ui.screen.income.IncomeFormScreen
import com.pfms.app.ui.screen.income.IncomeListScreen
import com.pfms.app.ui.screen.income.SalaryTemplateScreen

import com.pfms.app.viewmodel.AppLockState
import com.pfms.app.viewmodel.IncomeViewModel
import com.pfms.app.viewmodel.SalaryViewModel
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PfmsNavGraph(
    sessionViewModel: SessionViewModel = hiltViewModel(),
    incomeViewModel: IncomeViewModel = hiltViewModel(),
    salaryViewModel: SalaryViewModel = hiltViewModel()
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
                },

                onNavigateToAnalytics = {

                    navController.navigate(
                        Routes.IncomeAnalytics
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
            val incomeState by incomeViewModel.uiState.collectAsStateWithLifecycle()

            // Member 2: Realized Income excludes unpaid freelance and deducts crypto losses
            val nonCryptoRealized = incomeState.incomes.filter {
                if (it.source == "Freelance") it.paymentStatus == "Received"
                else it.source != "Crypto"
            }.sumOf { it.amountLkrMinor ?: it.amountMinor }

            val totalRealized = nonCryptoRealized + incomeState.cryptoGainsMinor - incomeState.cryptoLossesMinor

            IncomeListScreen(
                incomes = incomeState.incomes,
                isLoading = incomeState.isLoading,
                errorMessage = incomeState.errorMessage,

                totalRealizedIncomeMinor = totalRealized,
                totalPendingFreelanceMinor = incomeState.totalPendingFreelanceMinor,
                pendingFreelanceCount = incomeState.pendingFreelanceCount,
                cryptoGainsMinor = incomeState.cryptoGainsMinor,
                cryptoLossesMinor = incomeState.cryptoLossesMinor,
                netCryptoMinor = incomeState.netCryptoMinor,
                ytdNetCryptoMinor = incomeState.ytdNetCryptoMinor,

                onAddIncome = {
                    navController.navigate(Routes.AddIncome) {
                        launchSingleTop = true
                    }
                },

                onEditIncome = { incomeId ->
                    navController.navigate(Routes.editIncome(incomeId)) {
                        launchSingleTop = true
                    }
                },

                onDeleteIncome = { incomeId ->
                    incomeViewModel.deleteIncome(incomeId)
                },

                onMarkFreelanceReceived = { incomeId ->
                    incomeViewModel.markFreelanceAsReceived(incomeId)
                },

                onBack = {
                    navController.popBackStack()
                },

                onRecurringSalary = {
                    navController.navigate(Routes.SalaryTemplate) {
                        launchSingleTop = true
                    }
                },

                onNavigateToAnalytics = {
                    navController.navigate(Routes.IncomeAnalytics) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // -------------------------------------
        // ADD INCOME
        // -------------------------------------

        composable(Routes.AddIncome) {
            val incomeState by incomeViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(incomeState.saveSuccessful) {
                if (incomeState.saveSuccessful) {
                    incomeViewModel.clearSaveSuccess()
                    navController.popBackStack()
                }
            }

            IncomeFormScreen(
                onBack = {
                    incomeViewModel.clearExchangeRate()
                    navController.popBackStack()
                },

                onSave = { input ->
                    incomeViewModel.addIncome(
                        amount = input.amount,
                        currency = input.currency,
                        source = input.source,
                        receivedDate = input.receivedDate,
                        note = input.note,
                        projectName = input.projectName,
                        paymentStatus = input.paymentStatus,
                        cryptoType = input.cryptoType,
                        cryptoAsset = input.cryptoAsset
                    )
                },
                isSaving = incomeState.isSaving,
                errorMessage = incomeState.errorMessage,
                exchangeRate = incomeState.exchangeRate,
                exchangeRateDate = incomeState.exchangeRateDate,
                isRateFetching = incomeState.isRateFetching,
                rateFetchError = incomeState.rateFetchError,
                isManualRateMode = incomeState.isManualRateMode,
                onFetchExchangeRate = { date ->
                    incomeViewModel.fetchExchangeRate(date)
                },
                onSetManualRate = { rate ->
                    incomeViewModel.setManualExchangeRate(rate)
                },
                onClearExchangeRate = {
                    incomeViewModel.clearExchangeRate()
                }
            )
        }

        // -------------------------------------
        // EDIT INCOME
        // -------------------------------------

        composable(
            route = Routes.EditIncome,
            arguments = listOf(
                navArgument("incomeId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val incomeId = backStackEntry.arguments?.getString("incomeId")
            val incomeState by incomeViewModel.uiState.collectAsStateWithLifecycle()
            val incomeItem = incomeState.incomes.find { it.id == incomeId }

            LaunchedEffect(incomeState.saveSuccessful) {
                if (incomeState.saveSuccessful) {
                    incomeViewModel.clearSaveSuccess()
                    navController.popBackStack()
                }
            }

            if (incomeItem != null) {
                IncomeFormScreen(
                    initialIncome = incomeItem,
                    onBack = {
                        incomeViewModel.clearExchangeRate()
                        navController.popBackStack()
                    },
                    onSave = { input ->
                        incomeViewModel.updateIncome(
                            incomeId = incomeItem.id,
                            amount = input.amount,
                            currency = input.currency,
                            source = input.source,
                            receivedDate = input.receivedDate,
                            note = input.note,
                            projectName = input.projectName,
                            paymentStatus = input.paymentStatus,
                            cryptoType = input.cryptoType,
                            cryptoAsset = input.cryptoAsset
                        )
                    },
                    isSaving = incomeState.isSaving,
                    errorMessage = incomeState.errorMessage,
                    exchangeRate = incomeState.exchangeRate,
                    exchangeRateDate = incomeState.exchangeRateDate,
                    isRateFetching = incomeState.isRateFetching,
                    rateFetchError = incomeState.rateFetchError,
                    isManualRateMode = incomeState.isManualRateMode,
                    onFetchExchangeRate = { date ->
                        incomeViewModel.fetchExchangeRate(date)
                    },
                    onSetManualRate = { rate ->
                        incomeViewModel.setManualExchangeRate(rate)
                    },
                    onClearExchangeRate = {
                        incomeViewModel.clearExchangeRate()
                    }
                )
            } else if (!incomeState.isLoading) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Income Not Found") },
                            navigationIcon = {
                                TextButton(onClick = { navController.popBackStack() }) {
                                    Text("Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("This income record was not found.")
                    }
                }
            }
        }

        // -------------------------------------
        // RECURRING SALARY TEMPLATE
        // -------------------------------------

        composable(Routes.SalaryTemplate) {
            val salaryState by salaryViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(salaryState.operationSuccessful) {
                if (salaryState.operationSuccessful) {
                    salaryViewModel.clearSuccess()
                    navController.popBackStack()
                }
            }

            SalaryTemplateScreen(
                initialTemplate = salaryState.template,
                isSaving = salaryState.isSaving,
                errorMessage = salaryState.errorMessage,
                onBack = {
                    navController.popBackStack()
                },
                onSaveTemplate = { amount ->
                    salaryViewModel.saveSalaryTemplate(amount)
                }
            )
        }

        // -------------------------------------
        // INCOME ANALYTICS (MEMBER 3)
        // -------------------------------------

        composable(Routes.IncomeAnalytics) {
            IncomeAnalyticsScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
