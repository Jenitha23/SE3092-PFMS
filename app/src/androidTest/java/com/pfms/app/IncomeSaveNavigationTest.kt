package com.pfms.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.data.FakeSavingsGoalRepository
import com.pfms.app.domain.usecase.savings.CalculateCurrentMonthGoalContributionUseCase
import com.pfms.app.data.exchange.ExchangeRateService
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.repository.OfflineFirstIncomeRepository
import com.pfms.app.domain.model.Expense
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.DashboardRepository
import com.pfms.app.domain.usecase.dashboard.AppClock
import com.pfms.app.ui.navigation.IncomeSaveNavigationEffect
import com.pfms.app.ui.navigation.Routes
import com.pfms.app.ui.screen.income.IncomeFormScreen
import com.pfms.app.viewmodel.DashboardViewModel
import com.pfms.app.viewmodel.IncomeViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Real Compose back stack, income form, ViewModels and disk Room; only auth/remote are fakes. */
class IncomeSaveNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "income-navigation-${UUID.randomUUID()}.db"
    private lateinit var db: PfmsDatabase
    private lateinit var repository: OfflineFirstIncomeRepository
    private lateinit var income: IncomeViewModel
    private lateinit var dashboard: DashboardViewModel
    private lateinit var nav: NavHostController
    private val stores = ViewModelStore()
    private val auth = ExpenseTestAuth()
    private val remoteRows = ConcurrentHashMap<String, Income>()
    @Volatile private var online = false
    private val redraw = mutableIntStateOf(0)
    private val mounted = mutableStateOf(true)
    private val remote = object : IncomeRemoteDataSource {
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = remoteRows.values.toList()
        override suspend fun getIncomeById(userId: String, incomeId: String) = remoteRows[incomeId]
        override suspend fun upsertIncome(userId: String, income: Income) {
            check(online) { "Offline" }
            remoteRows[income.id] = income
        }
        override suspend fun deleteIncome(userId: String, incomeId: String) {
            check(online) { "Offline" }
            remoteRows.remove(incomeId)
        }
    }

    @Before fun setup() {
        db = openDatabase()
        repository = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth)
        compose.runOnIdle {
            income = IncomeViewModel(repository, auth, ExchangeRateService())
            dashboard = DashboardViewModel(repository, object : DashboardRepository {
                override fun observeExpenses(): Flow<List<Expense>> = flowOf(emptyList())
                override fun observeExpensesBetween(start: LocalDate, end: LocalDate) = observeExpenses()
            },
                savingsGoalRepository = FakeSavingsGoalRepository(),
                currentMonthGoalContribution = CalculateCurrentMonthGoalContributionUseCase()
            )
            stores.put("income", income)
            stores.put("dashboard", dashboard)
        }
    }

    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { stores.clear() }
        db.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() = Room.databaseBuilder(context, PfmsDatabase::class.java, databaseName).build()

    private fun host(repeatedCollector: Boolean = false, start: String = Routes.Dashboard) {
        compose.setContent {
            nav = rememberNavController()
            val state by income.uiState.collectAsStateWithLifecycle()
            val home by dashboard.uiState.collectAsStateWithLifecycle()
            Column {
                Text("Refresh ${redraw.intValue}", Modifier.testTag("redraw"))
                Text(home.summary?.totalIncomeMinor?.toString().orEmpty(), Modifier.testTag("dashboard-total"))
                if (mounted.value) IncomeSaveNavigationEffect(nav, income)
                NavHost(nav, startDestination = start) {
                    composable(Routes.Dashboard) { Text("Dashboard") }
                    composable(Routes.IncomeList) {
                        Column {
                            Text("Income list")
                            state.incomes.forEach { Text(it.note) }
                        }
                    }
                    composable(Routes.AddIncome) {
                        // Also collect the completion in a second composition to exercise stale snapshots.
                        if (repeatedCollector && mounted.value) IncomeSaveNavigationEffect(nav, income)
                        IncomeFormScreen(onBack = { nav.popBackStack() }, onSave = {
                            income.addIncome(it.amount, it.currency, it.source, it.receivedDate, it.note)
                        }, isSaving = state.isSaving, errorMessage = state.errorMessage)
                    }
                    composable(Routes.EditIncome) {
                        if (repeatedCollector && mounted.value) IncomeSaveNavigationEffect(nav, income)
                        Text("Edit income")
                    }
                    composable("other") { Text("Another screen") }
                }
            }
        }
        compose.waitUntil(10_000) { !income.uiState.value.isLoading }
    }

    private fun saveFromForm() {
        compose.onNodeWithTag("income_form_amount").performScrollTo().performTextInput("1234.56")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Save Income").performClick()
        compose.waitUntil(10_000) { income.uiState.value.incomes.size == 1 && !income.uiState.value.isSaving }
        compose.waitForIdle()
    }

    @Test fun offlineSaveFromIncomeListReturnsExactlyOnceAndUpdatesReaders() {
        host(repeatedCollector = true)
        compose.runOnIdle { nav.navigate(Routes.IncomeList); nav.navigate(Routes.AddIncome) }
        saveFromForm()
        compose.runOnIdle {
            assertEquals(Routes.IncomeList, nav.currentDestination?.route)
            assertFalse(income.uiState.value.saveSuccessful)
        }
        compose.onNodeWithText("Income list").assertIsDisplayed()
        compose.waitUntil(10_000) { dashboard.uiState.value.summary?.totalIncomeMinor == 123456L }
        runBlocking {
            val saved = db.incomeDao().observeIncome("offline-test-a").first().single()
            assertEquals(123456L, saved.amountMinor)
            assertEquals(SyncStatus.PENDING_INSERT, saved.syncStatus)
            assertTrue(remoteRows.isEmpty())
            assertTrue(repository.syncPendingIncome().isFailure)
        }
        repeat(3) { compose.runOnIdle { redraw.intValue++ } }
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle { mounted.value = true }
        compose.runOnIdle { nav.navigate(Routes.AddIncome) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Routes.AddIncome, nav.currentDestination?.route) }
        runBlocking { assertEquals(1, db.incomeDao().observeIncome("offline-test-a").first().size) }
    }

    @Test fun offlineSaveFromDashboardNeverEmptiesTheBackStack() {
        host(repeatedCollector = true)
        compose.runOnIdle { nav.navigate(Routes.AddIncome) }
        saveFromForm()
        compose.runOnIdle { assertEquals(Routes.Dashboard, nav.currentDestination?.route) }
        compose.onNodeWithText("Dashboard").assertIsDisplayed()
    }

    @Test fun onlineSaveSyncsOneRecordAndSurvivesDatabaseAndViewModelRestart() {
        online = true
        host()
        compose.runOnIdle { nav.navigate(Routes.IncomeList); nav.navigate(Routes.AddIncome) }
        saveFromForm()
        compose.runOnIdle { assertEquals(Routes.IncomeList, nav.currentDestination?.route) }
        runBlocking {
            assertTrue(repository.syncPendingIncome().isSuccess)
            assertEquals(1, remoteRows.size)
        }
        compose.runOnIdle { stores.clear() }
        db.close()
        db = openDatabase()
        repository = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth)
        runBlocking {
            val persisted = repository.observeIncome().first().single()
            assertEquals(remoteRows.values.single().id, persisted.id)
            assertEquals(123456L, persisted.amountMinor)
        }
        compose.runOnIdle {
            val restarted = IncomeViewModel(repository, auth, ExchangeRateService())
            stores.put("restarted", restarted)
            income = restarted
        }
        compose.waitUntil(10_000) { income.uiState.value.incomes.size == 1 }
        assertFalse(income.uiState.value.saveSuccessful)
    }

    @Test fun savedIncomeRemainsVisibleAfterOfflineDatabaseReopen() {
        host()
        compose.runOnIdle { nav.navigate(Routes.AddIncome) }
        saveFromForm()
        compose.runOnIdle { stores.clear() }
        db.close()
        db = openDatabase()
        runBlocking {
            val reopened = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth)
            assertEquals(123456L, reopened.observeIncome().first().single().amountMinor)
            assertEquals(SyncStatus.PENDING_INSERT, db.incomeDao().getPendingSync("offline-test-a").single().syncStatus)
            assertTrue(remoteRows.isEmpty())
        }
    }

    @Test fun saveWithoutAPreviousScreenFallsBackToIncomeList() {
        host(start = Routes.AddIncome)
        saveFromForm()
        compose.runOnIdle { assertEquals(Routes.IncomeList, nav.currentDestination?.route) }
        compose.onNodeWithText("Income list").assertIsDisplayed()
    }

    @Test fun completionOnAnotherScreenIsAcknowledgedWithoutNavigating() {
        host()
        compose.runOnIdle {
            nav.navigate("other")
            income.addIncome("1234.56", "LKR", "Salary", AppClock.today().toString(), "Late completion")
        }
        compose.waitUntil(10_000) { income.uiState.value.incomes.size == 1 && !income.uiState.value.saveSuccessful }
        compose.runOnIdle { assertEquals("other", nav.currentDestination?.route) }
        compose.onNodeWithText("Another screen").assertIsDisplayed()
    }

    @Test fun editSuccessReturnsToCallerOnceWithRepeatedCollectors() {
        host(repeatedCollector = true)
        compose.runOnIdle {
            income.addIncome("1234.56", "LKR", "Salary", AppClock.today().toString(), "Before edit")
        }
        compose.waitUntil(10_000) { income.uiState.value.incomes.size == 1 && !income.uiState.value.saveSuccessful }
        compose.runOnIdle {
            nav.navigate(Routes.IncomeList)
            val row = income.uiState.value.incomes.single()
            nav.navigate(Routes.editIncome(row.id))
        }
        compose.onNodeWithText("Edit income").assertIsDisplayed()
        compose.runOnIdle {
            val row = income.uiState.value.incomes.single()
            income.updateIncome(row.id, "4321.00", "LKR", row.source, row.receivedDate, "Edited")
        }
        compose.waitUntil(10_000) { income.uiState.value.incomes.single().amountMinor == 432100L }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Routes.IncomeList, nav.currentDestination?.route) }
        compose.onNodeWithText("Edited").assertIsDisplayed()
        runBlocking { assertEquals(1, db.incomeDao().observeIncome("offline-test-a").first().size) }
    }
}
