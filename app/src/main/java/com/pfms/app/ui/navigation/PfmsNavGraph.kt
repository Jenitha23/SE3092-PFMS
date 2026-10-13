
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
import com.pfms.app.viewmodel.IncomeViewModel
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
    incomeViewModel: IncomeViewModel = hiltViewModel()
) {

    val navController = rememberNavController()

    val session by sessionViewModel.session
        .collectAsStateWithLifecycle()

    val lockState by sessionViewModel.lockState
        .collectAsStateWithLifecycle()
    val incomeUiState by incomeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(incomeUiState.saveSuccessful) {
        if (incomeUiState.saveSuccessful) {
            incomeViewModel.clearSaveSuccess()
            navController.popBackStack()
        }
    }

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
                incomes = incomeUiState.incomes,
                isLoading = incomeUiState.isLoading,
                errorMessage = incomeUiState.errorMessage,
                totalRealizedIncomeMinor = incomeUiState.totalRealizedIncomeMinor,
                monthlyRealizedIncomeMinor = incomeUiState.monthlyRealizedIncomeMinor,
                totalPendingFreelanceMinor = incomeUiState.totalPendingFreelanceMinor,
                pendingFreelanceCount = incomeUiState.pendingFreelanceCount,
                cryptoGainsMinor = incomeUiState.cryptoGainsMinor,
                cryptoLossesMinor = incomeUiState.cryptoLossesMinor,
                netCryptoMinor = incomeUiState.netCryptoMinor,
                ytdNetCryptoMinor = incomeUiState.ytdNetCryptoMinor,
                onAddIncome = { navController.navigate(Routes.AddIncome) },
                onEditIncome = { id -> navController.navigate(Routes.editIncome(id)) },
                onDeleteIncome = incomeViewModel::deleteIncome,
                onMarkFreelanceReceived = incomeViewModel::markFreelanceAsReceived,
                onBack = { navController.popBackStack() },
                onRecurringSalary = { navController.navigate(Routes.SalaryTemplate) }
            )
        }

        // ADD INCOME – all validation and persistence happens in ViewModel.
        composable(Routes.AddIncome) {
            LaunchedEffect(Unit) {
                incomeViewModel.clearFormError()
                incomeViewModel.clearExchangeRate()
            }
            IncomeFormScreen(
                onBack = { navController.popBackStack() },
                onSave = { input ->
                    incomeViewModel.addIncome(
                        amount = input.amount, currency = input.currency,
                        source = input.source, receivedDate = input.receivedDate,
                        note = input.note, projectName = input.projectName,
                        paymentStatus = input.paymentStatus,
                        cryptoType = input.cryptoType, cryptoAsset = input.cryptoAsset
                    )
                },
                isSaving = incomeUiState.isSaving,
                errorMessage = incomeUiState.errorMessage,
                exchangeRate = incomeUiState.exchangeRate,
                exchangeRateDate = incomeUiState.exchangeRateDate,
                isRateFetching = incomeUiState.isRateFetching,
                rateFetchError = incomeUiState.rateFetchError,
                isManualRateMode = incomeUiState.isManualRateMode,
                onFetchExchangeRate = incomeViewModel::fetchExchangeRate,
                onSetManualRate = incomeViewModel::setManualExchangeRate,
                onEnableManualRate = incomeViewModel::enableManualRate,
                onClearExchangeRate = incomeViewModel::clearExchangeRate
            )
        }

        // EDIT INCOME – retains the conversion metadata on note/status-only edits.
        composable(
            route = Routes.EditIncome,
            arguments = listOf(navArgument("incomeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("incomeId")
            val item = incomeUiState.incomes.firstOrNull { it.id == id }
            LaunchedEffect(item?.id) {
                if (item != null) {
                    incomeViewModel.clearFormError()
                    incomeViewModel.loadExistingRate(item)
                }
            }
            when {
                item != null -> IncomeFormScreen(
                    initialIncome = item,
                    onBack = { navController.popBackStack() },
                    onSave = { input ->
                        incomeViewModel.updateIncome(
                            incomeId = item.id, amount = input.amount,
                            currency = input.currency, source = input.source,
                            receivedDate = input.receivedDate, note = input.note,
                            projectName = input.projectName, paymentStatus = input.paymentStatus,
                            cryptoType = input.cryptoType, cryptoAsset = input.cryptoAsset
                        )
                    },
                    isSaving = incomeUiState.isSaving,
                    errorMessage = incomeUiState.errorMessage,
                    exchangeRate = incomeUiState.exchangeRate,
                    exchangeRateDate = incomeUiState.exchangeRateDate,
                    isRateFetching = incomeUiState.isRateFetching,
                    rateFetchError = incomeUiState.rateFetchError,
                    isManualRateMode = incomeUiState.isManualRateMode,
                    onFetchExchangeRate = incomeViewModel::fetchExchangeRate,
                    onSetManualRate = incomeViewModel::setManualExchangeRate,
                    onEnableManualRate = incomeViewModel::enableManualRate,
                    onClearExchangeRate = incomeViewModel::clearExchangeRate
                )
                else -> Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(if (incomeUiState.isLoading) "Loading Income" else "Income Not Found") },
                            navigationIcon = {
                                TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (incomeUiState.isLoading) "Loading..." else "This income record was not found.")
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
