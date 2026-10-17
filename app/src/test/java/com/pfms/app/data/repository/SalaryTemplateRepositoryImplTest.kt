package com.pfms.app.data.repository

import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.dao.SalaryTemplateDao
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SalaryTemplateEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.data.remote.firestore.SalaryTemplateRemoteDataSource
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.SalaryTemplate
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class SalaryTemplateRepositoryImplTest {

    private lateinit var salaryTemplateDao: SalaryTemplateDao
    private lateinit var salaryTemplateRemoteDataSource: SalaryTemplateRemoteDataSource
    private lateinit var incomeDao: IncomeDao
    private lateinit var incomeRemoteDataSource: IncomeRemoteDataSource
    private lateinit var database: PfmsDatabase
    private lateinit var authRepository: AuthRepository
    private lateinit var repository: SalaryTemplateRepositoryImpl

    private val testUser = AuthUser(
        uid = "user-123",
        displayName = "Test User",
        email = "test@example.com",
        isEmailVerified = true
    )

    @Before
    fun setup() {
        salaryTemplateDao = mock()
        salaryTemplateRemoteDataSource = mock()
        whenever(salaryTemplateRemoteDataSource.observeSnapshots(any())).thenReturn(kotlinx.coroutines.flow.emptyFlow())
        incomeDao = mock()
        incomeRemoteDataSource = mock()
        database = mock()
        authRepository = mock()

        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))

        // Support withTransaction on mock PfmsDatabase
        val executor: Executor = Executors.newSingleThreadExecutor()
        whenever(database.transactionExecutor).thenReturn(executor)

        repository = SalaryTemplateRepositoryImpl(
            salaryTemplateDao = salaryTemplateDao,
            salaryTemplateRemoteDataSource = salaryTemplateRemoteDataSource,
            incomeDao = incomeDao,
            incomeRemoteDataSource = incomeRemoteDataSource,
            database = database,
            authRepository = authRepository
        ).apply {
            transactionRunner = { it() }
        }
    }

    @Test
    fun `saveSalaryTemplate saves locally with PENDING_UPDATE`() = runTest {
        val template = SalaryTemplate(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25
        )

        repository.saveSalaryTemplate(template)

        val captor = argumentCaptor<SalaryTemplateEntity>()
        verify(salaryTemplateDao).upsertSalaryTemplate(captor.capture())

        val saved = captor.firstValue
        assertEquals("tpl-1", saved.id)
        assertEquals("user-123", saved.userId)
        assertEquals(SyncStatus.PENDING_UPDATE, saved.syncStatus)
    }

    @Test
    fun `saveSalaryTemplate on remote failure retains pending state without throwing`() = runTest {
        whenever(salaryTemplateRemoteDataSource.upsertSalaryTemplate(any(), any()))
            .thenThrow(RuntimeException("Network offline"))

        val template = SalaryTemplate(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25
        )

        repository.saveSalaryTemplate(template)

        verify(salaryTemplateDao).upsertSalaryTemplate(any())
        verify(salaryTemplateDao, never()).updateSyncStatus("user-123", SyncStatus.SYNCED)
    }

    @Test
    fun `saveSalaryTemplate rejects cross-user access`() = runTest {
        val template = SalaryTemplate(
            id = "tpl-1",
            userId = "other-user-999",
            amountMinor = 25000000L
        )

        try {
            repository.saveSalaryTemplate(template)
            fail("Expected IllegalArgumentException for cross-user write")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cross-user write prevented") == true)
        }
        verify(salaryTemplateDao, never()).upsertSalaryTemplate(any())
    }

    @Test
    fun `observeSalaryTemplate streams from Room for authenticated user`() = runTest {
        val entity = SalaryTemplateEntity(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            isEnabled = true,
            syncStatus = SyncStatus.SYNCED,
            isDeleted = false
        )
        whenever(salaryTemplateDao.observeSalaryTemplate("user-123")).thenReturn(flowOf(entity))

        val result = repository.observeSalaryTemplate().first()

        assertNotNull(result)
        assertEquals("tpl-1", result?.id)
        assertEquals(25000000L, result?.amountMinor)
    }

    @Test
    fun `confirmMonthlySalary rejects confirmation when already confirmed for same month`() = runTest {
        val templateEntity = SalaryTemplateEntity(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            lastConfirmedMonth = "2026-10"
        )
        whenever(salaryTemplateDao.getSalaryTemplate("user-123")).thenReturn(templateEntity)

        try {
            repository.confirmMonthlySalary(
                templateId = "tpl-1",
                monthKey = "2026-10",
                amountMinor = 25000000L
            )
            fail("Expected IllegalStateException for duplicate month confirmation")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already been confirmed") == true)
        }
    }

    @Test
    fun `confirmMonthlySalary rejects when duplicate salary income already exists for month`() = runTest {
        val templateEntity = SalaryTemplateEntity(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            lastConfirmedMonth = "2026-09"
        )
        whenever(salaryTemplateDao.getSalaryTemplate("user-123")).thenReturn(templateEntity)

        val deterministicIncomeId = "salary_tpl-1_2026-10"
        val existingIncome = IncomeEntity(
            id = deterministicIncomeId,
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            source = "Salary",
            syncStatus = SyncStatus.SYNCED,
            isDeleted = false
        )
        whenever(incomeDao.getIncomeById("user-123", deterministicIncomeId)).thenReturn(existingIncome)

        try {
            repository.confirmMonthlySalary(
                templateId = "tpl-1",
                monthKey = "2026-10",
                amountMinor = 25000000L
            )
            fail("Expected IllegalStateException when duplicate income record exists")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("already exists") == true)
        }
    }

    @Test
    fun `confirmMonthlySalary rejects when templateId does not match user template`() = runTest {
        val templateEntity = SalaryTemplateEntity(
            id = "tpl-actual",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            lastConfirmedMonth = "2026-09"
        )
        whenever(salaryTemplateDao.getSalaryTemplate("user-123")).thenReturn(templateEntity)

        try {
            repository.confirmMonthlySalary(
                templateId = "tpl-wrong",
                monthKey = "2026-10",
                amountMinor = 25000000L
            )
            fail("Expected IllegalArgumentException for template ID mismatch")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Template ID mismatch") == true)
        }
    }

    @Test
    fun `confirmMonthlySalary creates one deterministic salary income and updates template`() = runTest {
        val templateEntity = SalaryTemplateEntity(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            lastConfirmedMonth = "2026-09"
        )
        whenever(salaryTemplateDao.getSalaryTemplate("user-123")).thenReturn(templateEntity)
        whenever(incomeDao.getIncomeById("user-123", "salary_tpl-1_2026-10")).thenReturn(null)

        repository.confirmMonthlySalary(
            templateId = "tpl-1",
            monthKey = "2026-10",
            amountMinor = 30000000L
        )

        // Verify income created with deterministic ID
        val incomeCaptor = argumentCaptor<IncomeEntity>()
        verify(incomeDao).upsertIncome(incomeCaptor.capture())
        assertEquals("salary_tpl-1_2026-10", incomeCaptor.firstValue.id)
        assertEquals(30000000L, incomeCaptor.firstValue.amountMinor)
        assertEquals("Salary", incomeCaptor.firstValue.source)
        assertEquals("user-123", incomeCaptor.firstValue.userId)

        // Verify template updated with monthKey and new amount
        val templateCaptor = argumentCaptor<SalaryTemplateEntity>()
        verify(salaryTemplateDao).upsertSalaryTemplate(templateCaptor.capture())
        assertEquals("2026-10", templateCaptor.firstValue.lastConfirmedMonth)
        assertEquals(30000000L, templateCaptor.firstValue.amountMinor)
    }

    @Test
    fun `syncPendingTemplate processes PENDING_INSERT and marks SYNCED`() = runTest {
        val pendingTemplate = SalaryTemplateEntity(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            lastConfirmedMonth = "2026-09",
            syncStatus = SyncStatus.PENDING_INSERT
        )
        whenever(salaryTemplateDao.getPendingSync("user-123")).thenReturn(listOf(pendingTemplate))

        val result = repository.syncPendingTemplate()

        assertTrue(result.isSuccess)
        verify(salaryTemplateRemoteDataSource).upsertSalaryTemplate(eq("user-123"), any())
        verify(salaryTemplateDao).updateSyncStatus("user-123", SyncStatus.SYNCED)
    }

    @Test
    fun `syncPendingTemplate returns failure and retains pending state when remote fails`() = runTest {
        val pendingTemplate = SalaryTemplateEntity(
            id = "tpl-1",
            userId = "user-123",
            amountMinor = 25000000L,
            currency = "LKR",
            paymentDay = 25,
            lastConfirmedMonth = "2026-09",
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        whenever(salaryTemplateDao.getPendingSync("user-123")).thenReturn(listOf(pendingTemplate))
        whenever(salaryTemplateRemoteDataSource.upsertSalaryTemplate(eq("user-123"), any()))
            .thenThrow(RuntimeException("Network failure"))

        val result = repository.syncPendingTemplate()

        assertTrue(result.isFailure)
        verify(salaryTemplateDao, never()).updateSyncStatus("user-123", SyncStatus.SYNCED)
    }

    @Test
    fun `syncPendingTemplate returns success when pending queue is empty`() = runTest {
        whenever(salaryTemplateDao.getPendingSync("user-123")).thenReturn(emptyList())

        val result = repository.syncPendingTemplate()

        assertTrue(result.isSuccess)
    }
}


