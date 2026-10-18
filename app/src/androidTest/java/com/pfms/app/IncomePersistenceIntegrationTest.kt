package com.pfms.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.remote.firestore.*
import com.pfms.app.data.repository.*
import com.pfms.app.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class IncomePersistenceIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "income-completion-${UUID.randomUUID()}.db"
    private lateinit var db: PfmsDatabase
    private val auth = ExpenseTestAuth()
    private var online = false
    private val incomeRows = mutableMapOf<String, Income>()
    private val salaryRows = mutableMapOf<String, SalaryTemplate>()
    private val incomeRemote = object : IncomeRemoteDataSource {
        override fun observeIncome(userId: String): Flow<List<Income>> = emptyFlow()
        override suspend fun getAllIncome(userId: String) = incomeRows.values.toList()
        override suspend fun getIncomeById(userId: String, incomeId: String) = incomeRows[incomeId]
        override suspend fun upsertIncome(userId: String, income: Income) { check(online); incomeRows[income.id] = income }
        override suspend fun deleteIncome(userId: String, incomeId: String) { check(online); incomeRows.remove(incomeId) }
    }
    private val salaryRemote = object : SalaryTemplateRemoteDataSource {
        override fun observeSalaryTemplates(userId: String): Flow<List<SalaryTemplate>> = emptyFlow()
        override suspend fun getAllSalaryTemplates(userId: String) = salaryRows.values.toList()
        override suspend fun getSalaryTemplateById(userId: String, templateId: String) = salaryRows[templateId]
        override suspend fun upsertSalaryTemplate(userId: String, template: SalaryTemplate) { check(online); salaryRows[template.id] = template }
        override suspend fun deleteSalaryTemplate(userId: String, templateId: String) { check(online); salaryRows.remove(templateId) }
    }
    private fun open() = Room.databaseBuilder(context, PfmsDatabase::class.java, name).build()
    private fun incomeRepo() = OfflineFirstIncomeRepository(db.incomeDao(), incomeRemote, auth)
    private fun salaryRepo() = SalaryTemplateRepositoryImpl(db.salaryTemplateDao(), salaryRemote, db.incomeDao(), incomeRemote, db, auth)
    @Before fun setup() { db = open() }
    @After fun close() { db.close(); context.deleteDatabase(name) }

    @Test fun incomeCrudPreservesUsdFieldsOfflineAndSyncsAfterDatabaseReopen() = runBlocking {
        var repo = incomeRepo()
        val income = Income(id = "usd", userId = "offline-test-a", amountMinor = 10000, currency = "USD",
            receivedDate = "2025-01-10", amountLkrMinor = 3054200, exchangeRate = "305.42", exchangeRateDate = "2025-01-10")
        repo.addIncome(income)
        repo.updateIncome(repo.getIncomeById("usd")!!.copy(note = "Edited offline"))
        db.close(); db = open(); repo = incomeRepo()
        assertEquals("305.42", repo.getIncomeById("usd")?.exchangeRate)
        assertEquals(3054200L, repo.getIncomeById("usd")?.amountLkrMinor)
        online = true
        assertTrue(repo.syncPendingIncome().isSuccess)
        assertEquals("Edited offline", incomeRows["usd"]?.note)
        online = false
        repo.deleteIncome("usd")
        assertNull(repo.getIncomeById("usd"))
        online = true
        assertTrue(repo.syncPendingIncome().isSuccess)
        assertTrue(incomeRows.isEmpty())
    }

    @Test fun salaryTemplateFromUiIsOwnedAndConcurrentConfirmationsCreateOneMonthlyIncome() = runBlocking {
        val repo = salaryRepo()
        repo.saveSalaryTemplate(SalaryTemplate(id = "form-id", amountMinor = 100000))
        val template = db.salaryTemplateDao().getSalaryTemplate("offline-test-a")!!
        assertEquals("offline-test-a", template.userId)
        val month = YearMonth.now().toString()
        val results = coroutineScope {
            (1..2).map { async { runCatching { repo.confirmMonthlySalary(template.id, month, 100000) } } }.awaitAll()
        }
        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, db.incomeDao().observeIncome("offline-test-a").first().size)
        db.close(); db = open()
        assertTrue(runCatching { salaryRepo().confirmMonthlySalary(template.id, month, 100000) }.isFailure)
        online = true
        assertTrue(incomeRepo().syncPendingIncome().isSuccess)
        assertTrue(salaryRepo().syncPendingTemplate().isSuccess)
        assertEquals(1, incomeRows.size)
        assertEquals(month, salaryRows.values.single().lastConfirmedMonth)
    }
}
