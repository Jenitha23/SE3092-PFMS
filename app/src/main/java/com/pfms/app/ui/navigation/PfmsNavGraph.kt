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
import com.pfms.app.ui.screen.expense.ExpenseReminderPermission
import com.pfms.app.viewmodel.ExpenseViewModel
import com.pfms.app.viewmodel.SessionViewModel
import com.pfms.app.viewmodel.IncomeOnboardingViewModel
import com.pfms.app.ui.screen.income.IncomeOnboardingScreen
import com.pfms.app.ui.screen.income.SalaryConfirmationDialog
import com.pfms.app.ui.screen.income.SalaryReminderLifecycle
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
    onReminderConsumed: () -> Unit = {}
) {

    val advancedExpenseViewModel: AdvancedExpenseViewModel = hiltViewModel()
    val onboarding: IncomeOnboardingViewModel = hiltViewModel()
    val onboardingState by onboarding.uiState.collectAsStateWithLifecycle()
    val expenseState by expenseViewModel.uiState.collectAsStateWithLifecycle()
    val salaryState by salaryViewModel.uiState.collectAsStateWithLifecycle()
    var showDashboardQuickAdd by rememberSaveable { mutableStateOf(false) }

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

                AppLockState.Unlocked -> if (onboardingState.required) Routes.IncomeOnboarding else Routes.Dashboard
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

    // Consume reminder destinations only after authentication and app unlock.
    LaunchedEffect(pendingReminderRoute, targetRoute) {
        val destination = Routes.reminderDestination(pendingReminderRoute)
        if (destination != null && targetRoute == Routes.Dashboard) {
            navController.navigate(destination) { launchSingleTop = true }
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
                onNavigateToAnalytics = {
                    navController.navigate(Routes.IncomeAnalytics) { launchSingleTop = true }
                },
                onNavigateToExpenses = {
                    navController.navigate(Routes.ExpenseList) { launchSingleTop = true }
                },
                onNavigateToAddExpense = {
                    navController.navigate(Routes.AddExpense) { launchSingleTop = true }
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
                },

                onNavigateToHome = {
                    navController.navigate(Routes.Dashboard) {
                        launchSingleTop = true
                    }
                },

                onNavigateToExpenses = {
                    navController.navigate(Routes.ExpenseList) {
                        launchSingleTop = true
                    }
                },

                onNavigateToGoals = null
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
                },
                onHome = { navController.navigate(Routes.Dashboard) { launchSingleTop = true } },
                onIncome = { navController.navigate(Routes.IncomeList) { launchSingleTop = true } },
                onExpenses = { navController.navigate(Routes.ExpenseList) { launchSingleTop = true } },
                onGoals = null
            )
        }

        // Member 2 feature screens
        composable(Routes.Categories) {
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            CategoriesScreen(advancedExpenseViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.RecurringExpenses) {
            ExpenseReminderPermission()
            LaunchedEffect(Unit) { advancedExpenseViewModel.ensureDefaults() }
            RecurringExpensesScreen(advancedExpenseViewModel, onBack = { navController.popBackStack() })
        }
        composable(Routes.Subscriptions) {
            ExpenseReminderPermission()
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
                    onGoals = null,
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
                    onGoals = null,
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
}
