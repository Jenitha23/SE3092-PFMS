package com.pfms.app

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pfms.app.data.exchange.*
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.repository.OfflineFirstIncomeRepository
import com.pfms.app.domain.model.*
import com.pfms.app.ui.screen.income.IncomeFormScreen
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.viewmodel.IncomeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Real Room, preferences, service, ViewModel and form. HTTP/auth are isolated test doubles. */
class IncomeExchangeRateIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefix = "exchange-test-${UUID.randomUUID()}"
    private val cacheContext = object : ContextWrapper(context) {
        override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences("$prefix-$name", mode)
    }
    private val auth = ExpenseTestAuth()
    private val today = LocalDate.now(ZoneId.of("Asia/Colombo")).toString()
    private lateinit var db: PfmsDatabase
    private var online = true
    private var quote = "305.42"
    private val stores = mutableListOf<ViewModelStore>()
    private var hideKeyboard: () -> Unit = {}
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        if (!online) throw IOException("Offline test transport")
        val date = LocalDate.parse(today).atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val body = """{"result":"success","base_code":"USD","time_last_update_utc":"$date","rates":{"LKR":$quote}}"""
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build()
    private val remote = object : IncomeRemoteDataSource {
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override suspend fun getAllIncome(userId: String): List<Income> = emptyList()
        override suspend fun getIncomeById(userId: String, incomeId: String): Income? = null
        override suspend fun upsertIncome(userId: String, income: Income) { throw IOException("Offline") }
        override suspend fun deleteIncome(userId: String, incomeId: String) { throw IOException("Offline") }
    }
    private fun open() = Room.databaseBuilder(context, PfmsDatabase::class.java, "$prefix.db").build()
    private fun repo() = OfflineFirstIncomeRepository(db.incomeDao(), remote, auth)
    private fun service() = ExchangeRateService(client).also { it.cache = ExchangeRateCache(cacheContext) }
    private fun newVm(): IncomeViewModel {
        lateinit var vm: IncomeViewModel
        compose.runOnIdle {
            val store = ViewModelStore().also { stores += it }
            vm = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = IncomeViewModel(repo(), auth, service()) as T
            })[IncomeViewModel::class.java]
        }
        return vm
    }
    @Before fun setup() { db = open() }
    @After fun cleanup() {
        compose.runOnIdle { stores.forEach { it.clear() } }
        db.close()
        context.deleteDatabase("$prefix.db")
        context.deleteSharedPreferences("$prefix-pfms_exchange_rates_v1")
    }
    private fun form(vm: IncomeViewModel) {
        compose.setContent {
            val state by vm.uiState.collectAsState()
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            SideEffect { hideKeyboard = { focus.clearFocus(); keyboard?.hide() } }
            PFMSTheme(darkTheme = false) {
                IncomeFormScreen({}, { vm.addIncome(it.amount, it.currency, it.source, it.receivedDate, it.note) },
                    isSaving = state.isSaving, errorMessage = state.errorMessage,
                    exchangeRate = state.exchangeRate, exchangeRateDate = state.exchangeRateDate,
                    isRateFetching = state.isRateFetching, rateFetchError = state.rateFetchError,
                    isManualRateMode = state.isManualRateMode, isCachedExchangeRate = state.isCachedExchangeRate,
                    isSavedExchangeRate = state.isSavedExchangeRate, onFetchExchangeRate = vm::fetchExchangeRate,
                    onClearExchangeRate = vm::clearExchangeRate, onEnableManualRate = vm::enableManualRate,
                    onSetManualRate = vm::setManualExchangeRate)
            }
        }
        compose.onNodeWithContentDescription("Income amount").performTextReplacement("100")
        compose.runOnIdle { hideKeyboard() }
        compose.onNode(hasText("USD") and hasClickAction()).performClick()
    }
    private fun ready(vm: IncomeViewModel) { compose.waitUntil(10_000) { !vm.uiState.value.isRateFetching && (vm.uiState.value.exchangeRate != null || vm.uiState.value.isManualRateMode) } }

    @Test fun cachedOfflineFormSavesOnceAndConversionSurvivesRestartReconnectAndAccountSwitch() = runBlocking {
        assertFalse((service().fetchLatestRate() as ExchangeRateResult.Success).isCached)
        online = false
        val vm = newVm(); form(vm); ready(vm)
        compose.onNodeWithText("Cached rate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("income_rate_date").performScrollTo().assertTextEquals("Exchange rate date: $today")
        compose.onNodeWithText("Cached USD/LKR rate from $today; it may not reflect the current rate. Verify before saving; the saved conversion remains fixed.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("income_form_conversion").performScrollTo().assertTextEquals("LKR 30,542.00")
        compose.onNodeWithText("Save Income").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.saveSuccessful }
        val record = repo().observeIncome().first().single()
        assertEquals(3054200L, record.amountLkrMinor); assertEquals(today, record.exchangeRateDate)
        assertEquals("305.42", record.exchangeRate)
        compose.runOnIdle { vm.addIncome("100", "USD", "Salary", today, "Repeated tap") }
        assertEquals(1, repo().observeIncome().first().size)
        // Recreate all persistence consumers without clearing the database or preferences.
        compose.runOnIdle { stores.forEach { it.clear() } }
        db.close(); db = open()
        assertEquals(record, repo().getIncomeById(record.id))
        assertTrue((service().fetchLatestRate() as ExchangeRateResult.Success).isCached)
        val restarted = newVm()
        online = true; quote = "310.75"
        compose.runOnIdle { restarted.fetchExchangeRate(today) }; ready(restarted)
        assertFalse(restarted.uiState.value.isCachedExchangeRate)
        assertEquals(BigDecimal("310.75"), restarted.uiState.value.exchangeRate)
        assertEquals(record, repo().getIncomeById(record.id))
        val user = auth.users.value!!
        auth.users.value = user.copy(uid = "exchange-test-other")
        compose.waitUntil(5_000) { restarted.uiState.value.exchangeRate == null }
        assertTrue(repo().observeIncome().first().isEmpty())
        online = false
        assertEquals(BigDecimal("310.75"), (service().fetchLatestRate() as ExchangeRateResult.Success).rate)
        auth.users.value = user
        assertEquals(record, repo().getIncomeById(record.id))
    }

    @Test fun expiredCacheShowsManualEntryAndInvalidInputCanBeCorrectedBeforeOfflineSave() = runBlocking {
        ExchangeRateCache(cacheContext).put(LocalDate.parse(today).minusDays(15).toString(), BigDecimal("290"))
        online = false
        val vm = newVm(); form(vm); ready(vm)
        assertNull(vm.uiState.value.exchangeRate)
        compose.onNodeWithText("Manual rate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("USD to LKR rate").performScrollTo().performTextReplacement("0")
        compose.runOnIdle { hideKeyboard() }
        compose.onNodeWithText("Save Income").performClick()
        assertTrue(repo().observeIncome().first().isEmpty())
        compose.onAllNodesWithText("Enter a valid positive USD/LKR exchange rate.").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("USD to LKR rate").performScrollTo().performTextReplacement("300.25")
        compose.runOnIdle { hideKeyboard() }
        compose.onNodeWithTag("income_rate_date").performScrollTo().assertTextEquals("Exchange rate date: $today")
        compose.onNodeWithTag("income_form_conversion").performScrollTo().assertTextEquals("LKR 30,025.00")
        compose.onNodeWithText("Save Income").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.saveSuccessful }
        assertEquals(3002500L, repo().observeIncome().first().single().amountLkrMinor)
        assertEquals(LocalDate.parse(today).minusDays(15).toString(), ExchangeRateCache(cacheContext).latest()!!.first)
    }
}
