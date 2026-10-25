package com.pfms.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.entity.toEntity
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.data.sync.ExpenseSyncScheduler
import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.analytics.GetMonthlyIncomeSummaryUseCase
import com.pfms.app.domain.usecase.savings.*
import com.pfms.app.ui.screen.savings.SavingsGoalScreen
import com.pfms.app.viewmodel.SavingsGoalViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** Real screen -> ViewModels -> repository -> atomic Room policy, with offline remote services. */
class SavingsContributionFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val auth = ExpenseTestAuth()
    private val uid = "offline-test-a"
    private val today = LocalDate.now(ZoneId.of("Asia/Colombo"))
    private lateinit var db: PfmsDatabase
    private lateinit var vm: SavingsGoalViewModel
    private val stores = ViewModelStore()
    @Volatile private var remoteWrites = 0
    private var releaseRemote: CompletableDeferred<Unit>? = null
    private val goal = SavingsGoal("goal", uid, "Emergency fund", 10000000, today.plusYears(1).toString(), 1000000)
    private val prior = GoalTransaction("prior", goal.id, uid, 2000000, GoalTransactionType.CONTRIBUTION,
        today.withDayOfMonth(1).toString(), createdAt = 1)
    private val expense = Expense(id = "monthly-expense", userId = uid, amountMinor = 7000000, date = today.toString())
    private val savingsRemote = object : SavingsGoalRemoteDataSource {
        override suspend fun upsertGoal(userId: String, goal: SavingsGoal) = Result.failure<Unit>(IOException("Offline"))
        override suspend fun deleteGoal(userId: String, goalId: String) = Result.failure<Unit>(IOException("Offline"))
        override suspend fun fetchGoals(userId: String) = Result.success(emptyList<SavingsGoal>())
        override suspend fun fetchGoalById(userId: String, goalId: String) = Result.success<SavingsGoal?>(null)
        override suspend fun fetchTransactionsForGoal(userId: String, goalId: String) = Result.success(emptyList<GoalTransaction>())
        override suspend fun upsertTransaction(userId: String, transaction: GoalTransaction): Result<Unit> {
            remoteWrites++
            releaseRemote?.await()
            return Result.failure(IOException("Offline"))
        }
        override suspend fun deleteTransaction(userId: String, transactionId: String) = Result.failure<Unit>(IOException("Offline"))
    }
    private val incomeRemote = object : IncomeRemoteDataSource {
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = emptyList<Income>()
        override suspend fun getIncomeById(userId: String, incomeId: String): Income? = null
        override suspend fun upsertIncome(userId: String, income: Income): Unit = error("Unused")
        override suspend fun deleteIncome(userId: String, incomeId: String): Unit = error("Unused")
    }
    private val expenseRemote = object : ExpenseRemoteDataSource {
        override fun observe(userId: String): Flow<ExpenseRemoteSnapshot> = emptyFlow()
        override suspend fun insert(userId: String, expense: Expense, allowDuplicate: Boolean): Unit = error("Unused")
        override suspend fun update(userId: String, expense: Expense): Unit = error("Unused")
        override suspend fun delete(userId: String, id: String): Unit = error("Unused")
    }

    @Before fun setup() = runBlocking {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        db = Room.inMemoryDatabaseBuilder(context, PfmsDatabase::class.java).build()
        db.savingsGoalDao().upsertGoal(goal.toEntity())
        db.incomeDao().upsertIncome(Income("monthly-income", uid, 10000000, receivedDate = today.toString()).toEntity(SyncStatus.PENDING_INSERT))
        db.expenseDao().upsert(expense.toEntity().copy(syncStatus = "PENDING_INSERT"))
        db.goalTransactionDao().upsertTransaction(prior.toEntity())
        val savings = OfflineFirstSavingsGoalRepository(db.savingsGoalDao(), db.goalTransactionDao(), savingsRemote, auth)
        val incomes = OfflineFirstIncomeRepository(db.incomeDao(), incomeRemote, auth)
        val expenses = OfflineFirstExpenseRepository(db, expenseRemote, auth, context, ExpenseSyncScheduler {})
        val saved = CalculateSavedAmountUseCase()
        val progress = CalculateGoalProgressUseCase(saved)
        val contribution = CalculateCurrentMonthGoalContributionUseCase()
        val monthly = CalculateMonthlyNetSavingsUseCase(GetMonthlyIncomeSummaryUseCase())
        val trailing = CalculateTrailingNetSavingsAverageUseCase(monthly)
        compose.runOnIdle {
            vm = SavingsGoalViewModel(savings, expenses, incomes, auth, saved, progress, contribution, monthly,
                trailing, CalculateGoalPaceUseCase(progress, contribution, monthly, trailing), CalculateDiscretionaryImpactUseCase())
            stores.put("savings", vm)
        }
        compose.setContent { SavingsGoalScreen(vm) }
        compose.waitUntil(10_000) { !vm.uiState.value.isLoading && vm.uiState.value.currentSavedMinor == 3000000L }
    }

    @After fun cleanup() {
        releaseRemote?.complete(Unit)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { stores.clear() }
        db.close()
    }
    private fun openContribution() {
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasText("Add Contribution"))
        compose.onNodeWithText("Add Contribution").performClick()
        compose.onNodeWithText("Savings from earlier months and bank balances are not included.", substring = true).assertExists()
    }
    private fun amount(value: String) {
        compose.onNodeWithText("Amount (LKR)").performScrollTo().performTextReplacement(value)
        Espresso.closeSoftKeyboard()
    }
    private fun save() { compose.onNodeWithTag("savings_form_save").performClick() }
    private fun waitForError(available: String) {
        compose.waitUntil(10_000) { vm.uiState.value.errorMessage != null && !vm.uiState.value.operationInProgress }
        compose.onNode(hasText("Contribution exceeds the remaining recorded monthly surplus", substring = true) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed().assertTextContains("Available: LKR $available", substring = true)
        compose.onNodeWithTag("savings_form_save").assertIsEnabled()
        assertFalse(vm.uiState.value.canRetry)
    }
    private fun waitForSuccess(savedMinor: Long, version: Long = 1) {
        compose.waitUntil(10_000) { vm.uiState.value.successVersion == version && vm.uiState.value.currentSavedMinor == savedMinor }
        compose.onNodeWithText("Amount (LKR)").assertDoesNotExist()
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasTestTag("goal_saved_amount"))
    }
    private fun ledger() = runBlocking { db.goalTransactionDao().getTransactionsForGoal(uid, goal.id) }

    @Test fun excessiveOfflineContributionKeepsFormOpenAndCorrectionRefreshesBalanceAndProgress() {
        openContribution()
        amount("15000"); save(); waitForError("10,000.00")
        val firstFailure = vm.uiState.value.failureVersion
        save() // An unchanged invalid retry must settle back to an editable form.
        compose.waitUntil(10_000) { vm.uiState.value.failureVersion > firstFailure }
        waitForError("10,000.00")
        compose.onNodeWithText("Amount (LKR)").assertTextContains("15000")
        assertEquals(3000000L, vm.uiState.value.currentSavedMinor)
        assertEquals(0L, vm.uiState.value.successVersion)
        assertEquals(listOf(prior.toEntity()), ledger())
        assertEquals(0, remoteWrites)
        amount("2500.75")
        compose.waitUntil(5_000) { vm.uiState.value.errorMessage == null }
        compose.onNode(hasText("Contribution exceeds", substring = true) and hasAnyAncestor(isDialog())).assertDoesNotExist()
        save(); waitForSuccess(3250075)
        assertEquals(32.50075, vm.uiState.value.progressPercent, .00001)
        assertEquals(2, ledger().size)
        compose.onNodeWithTag("goal_saved_amount").assertTextEquals("LKR 32,500.75")
        runBlocking { assertEquals(1, db.goalTransactionDao().getPendingTransactions(uid).size) }
    }

    @Test fun contributionEqualToTheRemainingSurplusPersistsOffline() {
        openContribution(); amount("10000"); save(); waitForSuccess(4000000)
        assertEquals(40.0, vm.uiState.value.progressPercent, .0001)
        assertEquals(2, ledger().size)
        assertEquals(1, remoteWrites)
        runBlocking { assertEquals(SyncStatus.PENDING_INSERT, db.goalTransactionDao().getPendingTransactions(uid).single().syncStatus) }
    }

    @Test fun zeroSurplusRejectsContributionEvenWhenGoalHasEarlierSavings() {
        runBlocking { db.expenseDao().upsert(expense.copy(amountMinor = 8000000).toEntity()) }
        compose.waitUntil(10_000) { vm.uiState.value.currentMonthNetSavingsMinor == 2000000L }
        openContribution(); amount("1"); save(); waitForError("0.00")
        assertEquals(3000000L, vm.uiState.value.currentSavedMinor)
        assertEquals(1, ledger().size)
        assertEquals(0, remoteWrites)
    }

    @Test fun invalidAmountsNeverReachTheLedgerOrRemote() {
        openContribution()
        for (input in listOf("", "0", "-1", "bad", "1.001")) {
            amount(input); save()
            compose.onNodeWithText("Transaction amount must be greater than zero").performScrollTo().assertIsDisplayed()
            assertEquals(1, ledger().size)
            assertEquals(3000000L, vm.uiState.value.currentSavedMinor)
            assertEquals(0, remoteWrites)
        }
    }

    @Test fun editingAndDeletingAContributionUseTheGuardedLedgerAndRefreshTheScreen() {
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasContentDescription("Edit Transaction"))
        compose.onNodeWithContentDescription("Edit Transaction").performClick()
        amount("35000"); save(); waitForError("30,000.00")
        assertEquals(2000000L, ledger().single().amountMinor)
        amount("25000"); save(); waitForSuccess(3500000)
        assertEquals(prior.id, ledger().single().id)
        compose.onNodeWithTag("savings_sections").performScrollToNode(hasContentDescription("Delete Transaction"))
        compose.onNodeWithContentDescription("Delete Transaction").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        waitForSuccess(1000000, version = 2)
        assertTrue(ledger().isEmpty())
        compose.onNodeWithTag("goal_saved_amount").assertTextEquals("LKR 10,000.00")
        openContribution(); amount("15000"); save(); waitForSuccess(2500000, version = 3)
        assertEquals(1500000L, ledger().single().amountMinor)
    }

    @Test fun loadingDisablesFieldsAndRapidTapsProduceOnlyOneContribution() {
        releaseRemote = CompletableDeferred()
        openContribution(); amount("100")
        compose.onNodeWithTag("savings_form_save").performTouchInput { doubleClick() }
        compose.waitUntil(5_000) { vm.uiState.value.operationInProgress }
        compose.onNodeWithTag("savings_form_save").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Amount (LKR)").assertIsNotEnabled()
        releaseRemote!!.complete(Unit)
        waitForSuccess(3010000)
        assertEquals(2, ledger().size)
        assertEquals(1, remoteWrites)
    }
}
