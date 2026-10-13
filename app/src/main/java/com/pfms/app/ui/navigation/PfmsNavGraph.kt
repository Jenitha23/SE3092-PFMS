
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
import com.pfms.app.domain.model.Income
import java.math.BigDecimal

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
@OptIn(ExperimentalMaterial3Api::class)
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

    // In-memory income list for testing Member 1 & 2 flows
    // until Member 3 completes Firestore repository sync.
    val demoIncomes = remember {
        mutableStateListOf<Income>(
            Income(
                id = "sample-freelance-1",
                userId = "test-user",
                amountMinor = 5000000L, // 50,000 LKR
                currency = "LKR",
                source = "Freelance",
                receivedDate = "2026-10-10",
                note = "Landing page design",
                projectName = "Acme Corp Web App",
                paymentStatus = "Expected",
                createdAt = System.currentTimeMillis() - 86400000
            ),
            Income(
                id = "sample-crypto-1",
                userId = "test-user",
                amountMinor = 1500000L, // 15,000 LKR
                currency = "LKR",
                source = "Crypto",
                receivedDate = "2026-10-09",
                note = "ETH staking payout",
                cryptoType = "Gain",
                cryptoAsset = "ETH",
                createdAt = System.currentTimeMillis() - 172800000
            )
        )
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

            // Member 2: Helper — LKR-equivalent for totals.
            // For LKR entries: amountMinor.
            // For USD entries with conversion: amountLkrMinor.
            // For USD entries WITHOUT conversion: 0L (not LKR).
            fun Income.lkrAmount(): Long = when {
                currency == "LKR" -> amountMinor
                amountLkrMinor != null -> amountLkrMinor
                else -> 0L
            }

            // Calculate Member 2 freelance summary
            val pendingFreelance = demoIncomes.filter {
                it.source == "Freelance" && it.paymentStatus == "Expected"
            }
            val pendingCount = pendingFreelance.size
            val pendingTotal = pendingFreelance.sumOf { it.lkrAmount() }

            // Calculate Member 2 crypto summary
            val cryptoGains = demoIncomes
                .filter { it.source == "Crypto" && it.cryptoType == "Gain" }
                .sumOf { it.lkrAmount() }
            val cryptoLosses = demoIncomes
                .filter { it.source == "Crypto" && it.cryptoType == "Loss" }
                .sumOf { it.lkrAmount() }

            // Member 2: Realized Income excludes unpaid freelance and deducts crypto losses
            val nonCryptoRealized = demoIncomes.filter {
                if (it.source == "Freelance") it.paymentStatus == "Received"
                else it.source != "Crypto"
            }.sumOf { it.lkrAmount() }

            val totalRealized = nonCryptoRealized + cryptoGains - cryptoLosses

            IncomeListScreen(
                incomes = demoIncomes.toList(),

                totalRealizedIncomeMinor = totalRealized,
                totalPendingFreelanceMinor = pendingTotal,
                pendingFreelanceCount = pendingCount,
                cryptoGainsMinor = cryptoGains,
                cryptoLossesMinor = cryptoLosses,
                netCryptoMinor = cryptoGains - cryptoLosses,
                ytdNetCryptoMinor = cryptoGains - cryptoLosses,

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
                    demoIncomes.removeAll { it.id == incomeId }
                },

                onMarkFreelanceReceived = { incomeId ->
                    val index = demoIncomes.indexOfFirst { it.id == incomeId }
                    if (index != -1) {
                        demoIncomes[index] = demoIncomes[index].copy(paymentStatus = "Received")
                    }
                },

                onBack = {
                    navController.popBackStack()
                },

                onRecurringSalary = {
                    navController.navigate(Routes.SalaryTemplate) {
                        launchSingleTop = true
                    }
                }
            )
        }

        // -------------------------------------
        // ADD INCOME
        // -------------------------------------

        composable(Routes.AddIncome) {

            IncomeFormScreen(
                onBack = {
                    navController.popBackStack()
                },

                onSave = { input ->
                    val parsedBigDecimal = input.amount.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                    val minor = parsedBigDecimal.multiply(BigDecimal(100)).toLong()
                    val id = java.util.UUID.randomUUID().toString()

                    val newIncome = Income(
                        id = id,
                        userId = (session as? SessionState.Authenticated)?.user?.uid ?: "user-1",
                        amountMinor = minor,
                        currency = input.currency,
                        source = input.source,
                        receivedDate = input.receivedDate,
                        note = input.note,
                        exchangeRate = input.exchangeRate,
                        exchangeRateDate = input.exchangeRateDate,
                        amountLkrMinor = input.amountLkrMinor,
                        projectName = input.projectName,
                        paymentStatus = input.paymentStatus,
                        cryptoType = input.cryptoType,
                        cryptoAsset = input.cryptoAsset,
                        createdAt = System.currentTimeMillis()
                    )

                    demoIncomes.add(0, newIncome)
                    navController.popBackStack()
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
            val incomeItem = demoIncomes.find { it.id == incomeId }

            if (incomeItem != null) {
                IncomeFormScreen(
                    initialIncome = incomeItem,
                    onBack = {
                        navController.popBackStack()
                    },
                    onSave = { input ->
                        val parsedBigDecimal = input.amount.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
                        val minor = parsedBigDecimal.multiply(BigDecimal(100)).toLong()
                        val index = demoIncomes.indexOfFirst { it.id == incomeId }
                        if (index != -1) {
                            demoIncomes[index] = incomeItem.copy(
                                amountMinor = minor,
                                currency = input.currency,
                                source = input.source,
                                receivedDate = input.receivedDate,
                                note = input.note,
                                exchangeRate = input.exchangeRate,
                                exchangeRateDate = input.exchangeRateDate,
                                amountLkrMinor = input.amountLkrMinor,
                                projectName = input.projectName,
                                paymentStatus = input.paymentStatus,
                                cryptoType = input.cryptoType,
                                cryptoAsset = input.cryptoAsset,
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        navController.popBackStack()
                    }
                )
            } else {
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
