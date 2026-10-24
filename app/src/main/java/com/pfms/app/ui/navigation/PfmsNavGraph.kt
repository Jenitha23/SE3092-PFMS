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
import androidx.navigation.compose.currentBackStackEntryAsState

import com.pfms.app.domain.model.SessionState
import com.pfms.app.ui.screen.DashboardScreen
import com.pfms.app.ui.screen.ForgotPasswordScreen
import com.pfms.app.ui.screen.LockScreen
import com.pfms.app.ui.screen.LoginScreen
import com.pfms.app.ui.screen.RegisterScreen
import com.pfms.app.ui.screen.SettingsScreen
import com.pfms.app.ui.screen.PrivacyPolicyScreen
import com.pfms.app.ui.screen.SplashScreen
import com.pfms.app.ui.screen.savings.SavingsGoalScreen
import com.pfms.app.ui.screen.analytics.SpendingAnalyticsScreen
import com.pfms.app.ui.screen.analytics.SpendingPatternsScreen
import com.pfms.app.ui.screen.savings.SavingsSimulatorScreen
import com.pfms.app.ui.screen.history.TransactionHistoryScreen
import com.pfms.app.domain.model.dashboard.Transaction
import com.pfms.app.domain.model.dashboard.TransactionType

import com.pfms.app.ui.screen.income.IncomeAnalyticsScreen
import com.pfms.app.ui.screen.income.IncomeFormScreen
import com.pfms.app.ui.screen.income.IncomeListScreen
import com.pfms.app.ui.screen.income.SalaryTemplateScreen
import com.pfms.app.ui.screen.income.SalaryConfirmationDialog
import com.pfms.app.ui.screen.income.SalaryReminderLifecycle

import com.pfms.app.ui.screen.expense.ExpenseListScreen
import com.pfms.app.ui.screen.expense.ExpenseFormScreen
import com.pfms.app.ui.screen.expense.QuickAddExpenseSheet
import com.pfms.app.ui.screen.expense.ExpenseDevelopmentCategories
import com.pfms.app.ui.screen.expense.ExpenseListContent
import com.pfms.app.ui.screen.expense.ExpenseFormContent
import com.pfms.app.ui.screen.expense.ExpenseQuickAddContent

import com.pfms.app.viewmodel.AppLockState
import com.pfms.app.viewmodel.IncomeViewModel
import com.pfms.app.viewmodel.SalaryViewModel
import com.pfms.app.viewmodel.AdvancedExpenseViewModel
import com.pfms.app.ui.screen.expense.CategoriesScreen
import com.pfms.app.ui.screen.expense.RecurringExpensesScreen
import com.pfms.app.viewmodel.ExpenseViewModel
import com.pfms.app.viewmodel.SettingsViewModel
import com.pfms.app.ui.screen.BiometricOnboardingDialog
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.viewmodel.IncomeOnboardingViewModel
import com.pfms.app.ui.screen.income.IncomeOnboardingScreen
import com.pfms.app.ui.screen.expense.ExpenseDuplicateDialog

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
    salaryViewModel: SalaryViewModel = hiltViewModel(),
    expenseViewModel: ExpenseViewModel = hiltViewModel(),
    pendingReminderRoute: String? = null,
    onReminderConsumed: () -> Unit = {},
    pendingReminderUser: String? = null
) {

    val biometricSettingsViewModel: SettingsViewModel = hiltViewModel()
    val biometricPromptShown by biometricSettingsViewModel.biometricPromptShown.collectAsStateWithLifecycle()
    val biometricEnabled by biometricSettingsViewModel.biometricEnabled.collectAsStateWithLifecycle()
    val biometricAvailable = remember { biometricSettingsViewModel.isBiometricAvailable() }
    val advancedExpenseViewModel: AdvancedExpenseViewModel = hiltViewModel()
    val onboarding: IncomeOnboardingViewModel = hiltViewModel()
    val onboardingState by onboarding.uiState.collectAsStateWithLifecycle()
    val expenseState by expenseViewModel.uiState.collectAsStateWithLifecycle()
    val salaryState by salaryViewModel.uiState.collectAsStateWithLifecycle()
    var showDashboardQuickAdd by rememberSaveable { mutableStateOf(false) }

    val navController = rememberNavController()

    // Tapping a transaction anywhere (dashboard, analytics, history) opens its edit screen.
    val openTransaction: (Transaction) -> Unit = { tx ->
        val route = if (tx.type == TransactionType.INCOME) Routes.editIncome(tx.id)
        else Routes.editExpense(tx.id)
        navController.navigate(route) { launchSingleTop = true }
    }

    val session by sessionViewModel.session
        .collectAsStateWithLifecycle()

    val lockState by sessionViewModel.lockState
        .collectAsStateWithLifecycle()
    val incomeUiState by incomeViewModel.uiState.collectAsStateWithLifecycle()
    val salaryUiState by salaryViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(incomeUiState.saveSuccessful) {
        if (incomeUiState.saveSuccessful) {
            incomeViewModel.clearSaveSuccess()
            navController.popBackStack()
        }
    }

    // -----------------------------------------
    // LIFECYCLE-AWARE APP LOCK (FR-04)
    // -----------------------------------------
    // Only lock after the app has actually been hidden for at least 45 seconds.
    // ON_PAUSE/ON_RESUME can occur during a permission dialog or account chooser,
    // which should not instantly lock an already authenticated user.
    val lockGracePeriodMs = 45_000L
    val lifecycleOwner = LocalLifecycleOwner.current
    val authenticatedNow by rememberUpdatedState(session is SessionState.Authenticated)
    var stoppedAtElapsedMs by remember { mutableLongStateOf(0L) }
    var stoppedWhileAuthenticated by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, sessionViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    stoppedWhileAuthenticated = authenticatedNow
                    stoppedAtElapsedMs = if (authenticatedNow) {
                        android.os.SystemClock.elapsedRealtime()
                    } else {
                        0L
                    }
                }

                Lifecycle.Event.ON_START -> {
                    val elapsedMs = if (stoppedAtElapsedMs != 0L) {
                        android.os.SystemClock.elapsedRealtime() - stoppedAtElapsedMs
                    } else {
                        0L
                    }
                    val shouldLock = stoppedWhileAuthenticated &&
                            authenticatedNow &&
                            stoppedAtElapsedMs != 0L &&
                            elapsedMs >= lockGracePeriodMs
                    stoppedAtElapsedMs = 0L
                    stoppedWhileAuthenticated = false
                    if (shouldLock) sessionViewModel.lockApp()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

                AppLockState.Unlocked -> if (onboardingState.required) Routes.IncomeOnboarding else Routes.Dashboard
            }
        }
    }

    // FR-10: Recheck the due salary on resume and after successful unlock.
    SalaryReminderLifecycle(
        onResume = {
            if (targetRoute == Routes.Dashboard) {
                salaryViewModel.refreshSalaryReminder()
            }
        }
    )

    LaunchedEffect(targetRoute) {
        if (targetRoute == Routes.Dashboard) {
            salaryViewModel.refreshSalaryReminder()
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

    // Consume reminder destinations only after authentication and app unlock.
    LaunchedEffect(pendingReminderRoute, pendingReminderUser, targetRoute, session) {
        val destination = Routes.reminderDestination(pendingReminderRoute)
        if (destination != null && targetRoute == Routes.Dashboard) {
            if (Routes.reminderMatchesUser(pendingReminderUser, (session as? SessionState.Authenticated)?.user?.uid)) {
                navController.navigate(destination) { launchSingleTop = true }
            }
            onReminderConsumed()
        }
    }

    if (targetRoute == Routes.Dashboard) {
        SalaryReminderLifecycle(salaryViewModel::refreshSalaryReminder)
        if (salaryState.showConfirmation && salaryState.template != null && expenseState.pendingDuplicate == null && !showDashboardQuickAdd) {
            SalaryConfirmationDialog(salaryState.template!!, salaryViewModel::confirmSalary,
                salaryViewModel::dismissConfirmation, salaryState.isSaving, salaryState.errorMessage)
        }
        if (expenseState.pendingDuplicate != null) {
            ExpenseDuplicateDialog(expenseState.isSaving, expenseViewModel::confirmDuplicateExpense, expenseViewModel::cancelDuplicateExpense)
        }
        if (showDashboardQuickAdd) {
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            val categoryState by advancedExpenseViewModel.categories.collectAsStateWithLifecycle()
            ExpenseQuickAddContent(expenseViewModel, categoryState.filter { !it.archived }.map {
                com.pfms.app.ui.screen.expense.ExpenseCategoryOption(it.id, it.name, it.classification)
            }, { showDashboardQuickAdd = false }, { showDashboardQuickAdd = false })
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash
    ) {
        composable(Routes.IncomeOnboarding) { IncomeOnboardingScreen(onboardingState, onboarding::save) }


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
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(Routes.PrivacyPolicy) { launchSingleTop = true }
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
                onQuickAddExpense = { showDashboardQuickAdd = true },
                sessionViewModel = sessionViewModel,
                onNavigateToSettings = {
                    navController.navigate(Routes.Settings) { launchSingleTop = true }
                },
                onNavigateToIncome = {
                    navController.navigate(Routes.IncomeList) { launchSingleTop = true }
                },
                onNavigateToAddIncome = {
                    navController.navigate(Routes.AddIncome) { launchSingleTop = true }
                },
                // "Insights" opens spending analytics (FR-30..32). Income analytics stays reachable
                // from the income list and from a link inside spending analytics.
                onNavigateToAnalytics = {
                    navController.navigate(Routes.SpendingAnalytics) { launchSingleTop = true }
                },
                onNavigateToExpenses = {
                    navController.navigate(Routes.ExpenseList) {
                        launchSingleTop = true
                    }
                },

                onNavigateToGoals = {
                    navController.navigate(Routes.SavingsGoal) { launchSingleTop = true }
                },
                onNavigateToAddExpense = {
                    navController.navigate(Routes.AddExpense) { launchSingleTop = true }
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.TransactionHistory) { launchSingleTop = true }
                },
                onTransactionClick = openTransaction
            )
        }

        // -------------------------------------
        // SPENDING ANALYTICS (FR-30..32)
        // -------------------------------------

        composable(Routes.SpendingPatterns) {
            SpendingPatternsScreen(onBack = { navController.popBackStack() },
                onOpenSavingsSimulator = { navController.navigate(Routes.SavingsSimulator) { launchSingleTop = true } })
        }

        composable(Routes.SavingsSimulator) {
            SavingsSimulatorScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SpendingAnalytics) {
            SpendingAnalyticsScreen(
                onOpenSpendingPatterns = { navController.navigate(Routes.SpendingPatterns) { launchSingleTop = true } },
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.AddExpense) { launchSingleTop = true } },
                onAddIncome = { navController.navigate(Routes.AddIncome) { launchSingleTop = true } },
                onTransactionClick = openTransaction,
                onOpenHistory = {
                    navController.navigate(Routes.TransactionHistory) { launchSingleTop = true }
                },
                onOpenIncomeAnalytics = {
                    navController.navigate(Routes.IncomeAnalytics) { launchSingleTop = true }
                }
            )
        }

        // -------------------------------------
        // TRANSACTION HISTORY (FR-33)
        // -------------------------------------

        composable(Routes.TransactionHistory) {
            TransactionHistoryScreen(
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(Routes.AddExpense) { launchSingleTop = true } },
                onAddIncome = { navController.navigate(Routes.AddIncome) { launchSingleTop = true } },
                onTransactionClick = openTransaction
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
                },
                onNavigateToPrivacyPolicy = {
                    navController.navigate(Routes.PrivacyPolicy) { launchSingleTop = true }
                }
            )
        }

        composable(Routes.PrivacyPolicy) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        // -------------------------------------
        // SAVINGS GOAL (FR-23 through FR-28)
        // -------------------------------------

        composable(Routes.SavingsGoal) {

            SavingsGoalScreen(
                onOpenSavingsSimulator = { navController.navigate(Routes.SavingsSimulator) { launchSingleTop = true } },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.Dashboard) {
                            launchSingleTop = true
                        }
                    }
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
            val incomeStream by incomeViewModel.streamState.collectAsStateWithLifecycle()

            IncomeListScreen(
                state = incomeStream,
                actions = com.pfms.app.ui.screen.income.IncomeStreamActions(
                onRetry = incomeViewModel::retry,
                onPreviousMonth = incomeViewModel::previousStreamMonth,
                onNextMonth = incomeViewModel::nextStreamMonth,
                onShowMonths = incomeViewModel::showStreamMonths,
                onMonth = incomeViewModel::selectStreamMonth,
                onFilter = incomeViewModel::filterStream,
                onSort = incomeViewModel::toggleStreamSort,
                onMenu = incomeViewModel::showStreamRecordMenu,
                onDelete = incomeViewModel::requestStreamDelete,
                onConfirmDelete = incomeViewModel::confirmStreamDelete,
                onAdd = {
                    navController.navigate(Routes.AddIncome) {
                        launchSingleTop = true
                    }
                },

                onEdit = { incomeId ->
                    navController.navigate(Routes.editIncome(incomeId)) {
                        launchSingleTop = true
                    }
                },

                onMarkReceived = { incomeId ->
                    incomeViewModel.markFreelanceAsReceived(incomeId)
                },

                onBack = {
                    navController.popBackStack()
                },

                onSalary = {
                    navController.navigate(Routes.SalaryTemplate) {
                        launchSingleTop = true
                    }
                },

                onAnalytics = {
                    navController.navigate(Routes.IncomeAnalytics) {
                        launchSingleTop = true
                    }
                },

                onHome = {
                    navController.navigate(Routes.Dashboard) {
                        launchSingleTop = true
                    }
                },

                onExpenses = {
                    navController.navigate(Routes.ExpenseList) {
                        launchSingleTop = true
                    }
                },

                onGoals = { navController.navigate(Routes.SavingsGoal) { launchSingleTop = true } }
                )
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
                    incomeViewModel.discardDraft()
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
                canRetry = incomeState.canRetry,
                exchangeRate = incomeState.exchangeRate,
                exchangeRateDate = incomeState.exchangeRateDate,
                isRateFetching = incomeState.isRateFetching,
                rateFetchError = incomeState.rateFetchError,
                isManualRateMode = incomeState.isManualRateMode,
                onFetchExchangeRate = { date ->
                    incomeViewModel.fetchExchangeRate(date)
                },
                onSetManualRate = incomeViewModel::setManualExchangeRate,
                onEnableManualRate = incomeViewModel::enableManualRate,
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
            LaunchedEffect(incomeItem?.id) {
                incomeItem?.let(incomeViewModel::loadExistingRate)
            }

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
                        incomeViewModel.discardDraft()
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
                    canRetry = incomeState.canRetry,
                    exchangeRate = incomeState.exchangeRate,
                    exchangeRateDate = incomeState.exchangeRateDate,
                    isRateFetching = incomeState.isRateFetching,
                    rateFetchError = incomeState.rateFetchError,
                    isManualRateMode = incomeState.isManualRateMode,
                    onFetchExchangeRate = { date ->
                        incomeViewModel.fetchExchangeRate(date)
                    },
                    onSetManualRate = incomeViewModel::setManualExchangeRate,
                    onEnableManualRate = incomeViewModel::enableManualRate,
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
                onRetry = if (salaryState.canRetry) salaryViewModel::retry else null,
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
                onAddIncome = { navController.navigate(Routes.AddIncome) { launchSingleTop = true } },
                onBack = {
                    navController.popBackStack()
                },
                onHome = { navController.navigate(Routes.Dashboard) { launchSingleTop = true } },
                onIncome = { navController.navigate(Routes.IncomeList) { launchSingleTop = true } },
                onExpenses = { navController.navigate(Routes.ExpenseList) { launchSingleTop = true } },
                onGoals = { navController.navigate(Routes.SavingsGoal) { launchSingleTop = true } }
            )
        }

        // Member 2 feature screens
        composable(Routes.Categories) {
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            CategoriesScreen(advancedExpenseViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.RecurringExpenses) {
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            RecurringExpensesScreen(advancedExpenseViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.Subscriptions) {
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            RecurringExpensesScreen(advancedExpenseViewModel, onBack = { navController.popBackStack() }, subscriptionsOnly = true)
        }

        // =====================================
        // MEMBER 1 - EXPENSE MANAGEMENT
        // =====================================
        // With expenseViewModel == null, navigation previews are shown
        // with no save/delete action. Supplying a backed ViewModel
        // enables the connected screens without rewriting the routes.
        composable(Routes.ExpenseList) {
            var showQuickAdd by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            val categoryState by advancedExpenseViewModel.categories.collectAsStateWithLifecycle()
            val categories = categoryState.filter { !it.archived }.map {
                com.pfms.app.ui.screen.expense.ExpenseCategoryOption(it.id, it.name, it.classification)
            }
            val vm = expenseViewModel
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {

                    if (vm != null) {
                        ExpenseListContent(
                            viewModel = vm,
                            onCategories = { navController.navigate(Routes.Categories) },
                            onRecurring = { navController.navigate(Routes.RecurringExpenses) },
                            onSubscriptions = { navController.navigate(Routes.Subscriptions) },
                            onHome = { navController.navigate(Routes.Dashboard) { launchSingleTop = true } },
                            onIncome = { navController.navigate(Routes.IncomeList) { launchSingleTop = true } },
                            onGoals = { navController.navigate(Routes.SavingsGoal) { launchSingleTop = true } },
                            onInsights = { navController.navigate(Routes.IncomeAnalytics) { launchSingleTop = true } },
                            onAddExpense = { navController.navigate(Routes.AddExpense) },
                            onQuickAdd = {
                                vm.clearSuccess()
                                vm.clearError()
                                showQuickAdd = true
                            },
                            onEditExpense = { id ->
                                navController.navigate(Routes.editExpense(id))
                            },
                            onRepeatExpense = { id ->
                                navController.navigate(Routes.repeatExpense(id))
                            },
                            onBack = {
                                if (!navController.popBackStack()) {
                                    navController.navigate(Routes.Dashboard) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )

                        if (showQuickAdd) {
                            ExpenseQuickAddContent(
                                viewModel = vm,
                                categories = categories,
                                onDismiss = { showQuickAdd = false },
                                onSaveSuccess = { showQuickAdd = false }
                            )
                        }
                    } else {
                        val pending = "Expense storage is not connected yet. Saving is unavailable."
                        ExpenseListScreen(
                            expenses = emptyList(),
                            isLoading = false,
                            isDeleting = false,
                            onAddExpense = { navController.navigate(Routes.AddExpense) },
                            onQuickAdd = { showQuickAdd = true },
                            onEditExpense = { id ->
                                navController.navigate(Routes.editExpense(id))
                            },
                            onDeleteExpense = {},
                            onHome = { navController.navigate(Routes.Dashboard) { launchSingleTop = true } },
                            onIncome = { navController.navigate(Routes.IncomeList) { launchSingleTop = true } },
                            onGoals = { navController.navigate(Routes.SavingsGoal) { launchSingleTop = true } },
                            onInsights = { navController.navigate(Routes.IncomeAnalytics) { launchSingleTop = true } },
                            onBack = {
                                if (!navController.popBackStack()) {
                                    navController.navigate(Routes.Dashboard) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            errorMessage = pending
                        )
                        if (showQuickAdd) {
                            QuickAddExpenseSheet(
                                categories = categories,
                                defaultPaymentMethod =
                                    com.pfms.app.domain.model.ExpensePaymentMethod.CASH,
                                isSaving = false,
                                onSave = { _, _, _, _ -> },
                                onDismiss = { showQuickAdd = false },
                                errorMessage = pending
                            )
                        }
                    }
                }
            }
        }

        composable(Routes.AddExpense) {
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            val categoryState by advancedExpenseViewModel.categories.collectAsStateWithLifecycle()
            val activeCategoryOptions = categoryState.filter { !it.archived }.map {
                com.pfms.app.ui.screen.expense.ExpenseCategoryOption(it.id, it.name, it.classification)
            }
            val vm = expenseViewModel
            if (vm != null) {
                ExpenseFormContent(
                    viewModel = vm,
                    categories = activeCategoryOptions,
                    onManageRecurring = { navController.navigate(Routes.RecurringExpenses) },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.Dashboard) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onSaveSuccess = { navController.popBackStack() }
                )
            } else {
                ExpenseFormScreen(
                    initialExpense = null,
                    categories = activeCategoryOptions,
                    isSaving = false,
                    onSave = {},
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.Dashboard) {
                                launchSingleTop = true
                            }
                        }
                    },
                    errorMessage = "Expense storage is not connected yet. Nothing will be saved."
                )
            }
        }

        composable(
            route = Routes.EditExpense,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.StringType }
            )
        ) { entry ->
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            val categoryState by advancedExpenseViewModel.categories.collectAsStateWithLifecycle()
            val activeCategoryOptions = categoryState.filter { !it.archived }.map {
                com.pfms.app.ui.screen.expense.ExpenseCategoryOption(it.id, it.name, it.classification)
            }
            val vm = expenseViewModel
            val expenseId = entry.arguments?.getString("expenseId")
            if (vm != null && expenseId != null) {
                ExpenseFormContent(
                    viewModel = vm,
                    expenseId = expenseId,
                    categories = activeCategoryOptions,
                    onManageRecurring = { navController.navigate(Routes.RecurringExpenses) },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.Dashboard) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onSaveSuccess = { navController.popBackStack() }
                )
            } else {
                Scaffold { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Edit Expense", style = MaterialTheme.typography.headlineSmall)
                        Text("Editing requires the shared expense repository.")
                        TextButton(onClick = { navController.popBackStack() }) {
                            Text("Back")
                        }
                    }
                }
            }
        }

        composable(
            route = Routes.RepeatExpense,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.StringType }
            )
        ) { entry ->
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            val categoryState by advancedExpenseViewModel.categories.collectAsStateWithLifecycle()
            val activeCategoryOptions = categoryState.filter { !it.archived }.map {
                com.pfms.app.ui.screen.expense.ExpenseCategoryOption(it.id, it.name, it.classification)
            }
            val vm = expenseViewModel
            val expenseId = entry.arguments?.getString("expenseId")
            if (vm != null && expenseId != null) {
                ExpenseFormContent(
                    viewModel = vm,
                    expenseId = expenseId,
                    isRepeating = true,
                    categories = activeCategoryOptions,
                    onManageRecurring = { navController.navigate(Routes.RecurringExpenses) },
                    onBack = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.ExpenseList)
                        }
                    },
                    onSaveSuccess = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.ExpenseList)
                        }
                    }
                )
            }
        }
    }

    // FR-10: Show the monthly salary prompt above the navigation host.
    // Do not display it during login, while locked, or on form screens.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    // FR-04: Ask once whether the signed-in user wants an optional biometric app lock.
    // Wait until onboarding and unlocking are complete; never interrupt authentication.
    val showBiometricOffer =
        targetRoute == Routes.Dashboard &&
                currentRoute == Routes.Dashboard &&
                biometricPromptShown == false &&
                !biometricEnabled &&
                biometricAvailable &&
                !salaryUiState.showConfirmation &&
                expenseState.pendingDuplicate == null &&
                !showDashboardQuickAdd

    if (showBiometricOffer) {
        BiometricOnboardingDialog(
            onEnable = { biometricSettingsViewModel.enableBiometricFromPrompt() },
            onNotNow = { biometricSettingsViewModel.dismissBiometricPrompt() }
        )
    }

    val canShowSalaryReminder =
        targetRoute == Routes.Dashboard &&
                (currentRoute == Routes.Dashboard || currentRoute == Routes.IncomeList)

    // A salary confirmation should not pop the dashboard navigation stack.
    // The salary template screen retains its own success handler.
    LaunchedEffect(salaryUiState.operationSuccessful, canShowSalaryReminder) {
        if (salaryUiState.operationSuccessful && canShowSalaryReminder) {
            salaryViewModel.clearSuccess()
        }
    }

    val salaryTemplate = salaryUiState.template
    if (
        canShowSalaryReminder &&
        !salaryUiState.isLoading &&
        salaryUiState.showConfirmation &&
        salaryTemplate != null
    ) {
        SalaryConfirmationDialog(
            template = salaryTemplate,
            isSaving = salaryUiState.isSaving,
            errorMessage = salaryUiState.errorMessage,
            onConfirm = { amount -> salaryViewModel.confirmSalary(amount) },
            onDismiss = { salaryViewModel.dismissConfirmation() }
        )
    }
}
