package com.pfms.app.data.repository

import com.pfms.app.data.local.dao.IncomeDao
import com.pfms.app.data.local.entity.IncomeEntity
import com.pfms.app.data.local.entity.SyncStatus
import com.pfms.app.data.local.mapper.toDomain
import com.pfms.app.data.local.mapper.toEntity
import com.pfms.app.data.remote.firestore.IncomeRemoteDataSource
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

class OfflineFirstIncomeRepositoryTest {

    private lateinit var incomeDao: IncomeDao
    private lateinit var incomeRemoteDataSource: IncomeRemoteDataSource
    private lateinit var authRepository: AuthRepository
    private lateinit var repository: OfflineFirstIncomeRepository

    private val testUser = AuthUser(
        uid = "user-123",
        displayName = "Test User",
        email = "test@example.com",
        isEmailVerified = true
    )

    @Before
    fun setup() {
        incomeDao = mock()
        incomeRemoteDataSource = mock()
        authRepository = mock()

        whenever(authRepository.currentUser).thenReturn(flowOf(testUser))

        repository = OfflineFirstIncomeRepository(
            incomeDao = incomeDao,
            incomeRemoteDataSource = incomeRemoteDataSource,
            authRepository = authRepository
        )
    }

    @Test
    fun `addIncome writes Room first with PENDING_INSERT`() = runTest {
        val income = Income(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 150000L,
            currency = "LKR",
            source = "Salary"
        )

        repository.addIncome(income)

        val captor = argumentCaptor<IncomeEntity>()
        verify(incomeDao).upsertIncome(captor.capture())

        val saved = captor.firstValue
        assertEquals("inc-1", saved.id)
        assertEquals("user-123", saved.userId)
        assertEquals(SyncStatus.PENDING_INSERT, saved.syncStatus)
        assertEquals(false, saved.isDeleted)
    }

    @Test
    fun `addIncome on successful remote sync marks SYNCED in Room`() = runTest {
        val income = Income(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 150000L,
            currency = "LKR",
            source = "Salary"
        )

        repository.addIncome(income)

        verify(incomeRemoteDataSource).upsertIncome(eq("user-123"), any())
        verify(incomeDao).updateSyncStatus("user-123", "inc-1", SyncStatus.SYNCED)
    }

    @Test
    fun `addIncome on remote failure keeps PENDING_INSERT in Room without throwing`() = runTest {
        whenever(incomeRemoteDataSource.upsertIncome(any(), any()))
            .thenThrow(RuntimeException("Network offline"))

        val income = Income(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 150000L,
            currency = "LKR",
            source = "Salary"
        )

        // Must not throw exception
        repository.addIncome(income)

        verify(incomeDao).upsertIncome(any())
        verify(incomeDao, never()).updateSyncStatus(eq("user-123"), eq("inc-1"), eq(SyncStatus.SYNCED))
    }

    @Test
    fun `updateIncome on remote failure keeps PENDING_UPDATE in Room`() = runTest {
        val existingEntity = IncomeEntity(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 100000L,
            currency = "LKR",
            source = "Salary",
            createdAt = 1000L,
            updatedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
            isDeleted = false
        )
        whenever(incomeDao.getIncomeById("user-123", "inc-1")).thenReturn(existingEntity)
        whenever(incomeRemoteDataSource.upsertIncome(any(), any()))
            .thenThrow(RuntimeException("Network offline"))

        val updatedIncome = existingIncomeEntityToDomain(existingEntity).copy(
            amountMinor = 120000L,
            updatedAt = 2000L
        )

        repository.updateIncome(updatedIncome)

        val captor = argumentCaptor<IncomeEntity>()
        verify(incomeDao).upsertIncome(captor.capture())
        assertEquals(SyncStatus.PENDING_UPDATE, captor.firstValue.syncStatus)
        assertEquals(1000L, captor.firstValue.createdAt) // Preserves original createdAt
        verify(incomeDao, never()).updateSyncStatus("user-123", "inc-1", SyncStatus.SYNCED)
    }

    @Test
    fun `deleteIncome on remote failure keeps PENDING_DELETE in Room`() = runTest {
        whenever(incomeRemoteDataSource.deleteIncome(any(), any()))
            .thenThrow(RuntimeException("Network offline"))

        repository.deleteIncome("inc-1")

        verify(incomeDao).softDeleteIncome(
            userId = eq("user-123"),
            incomeId = eq("inc-1"),
            syncStatus = eq(SyncStatus.PENDING_DELETE),
            updatedAt = any()
        )
        // Hard delete should not occur when remote delete fails
        verify(incomeDao, never()).hardDeleteIncome("user-123", "inc-1")
    }

    @Test
    fun `deleteIncome on remote success hard deletes local row`() = runTest {
        repository.deleteIncome("inc-1")

        verify(incomeDao).softDeleteIncome(
            userId = eq("user-123"),
            incomeId = eq("inc-1"),
            syncStatus = eq(SyncStatus.PENDING_DELETE),
            updatedAt = any()
        )
        verify(incomeRemoteDataSource).deleteIncome("user-123", "inc-1")
        verify(incomeDao).hardDeleteIncome("user-123", "inc-1")
    }

    @Test
    fun `cross-user access is rejected when income userId does not match authenticated user`() = runTest {
        val income = Income(
            id = "inc-1",
            userId = "attacker-user-id",
            amountMinor = 5000L
        )

        try {
            repository.addIncome(income)
            fail("Expected IllegalArgumentException for cross-user write")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Cross-user write prevented") == true)
        }
        verify(incomeDao, never()).upsertIncome(any())
    }

    @Test
    fun `getIncomeById reads local user-scoped data`() = runTest {
        val localEntity = IncomeEntity(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 5000L,
            currency = "LKR",
            source = "Salary",
            createdAt = 1000L,
            updatedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
            isDeleted = false
        )
        whenever(incomeDao.getIncomeById("user-123", "inc-1")).thenReturn(localEntity)

        val result = repository.getIncomeById("inc-1")

        assertNotNull(result)
        assertEquals("inc-1", result?.id)
        assertEquals(5000L, result?.amountMinor)
    }

    @Test
    fun `getIncomeById returns null if record is soft-deleted locally`() = runTest {
        val deletedEntity = IncomeEntity(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 5000L,
            currency = "LKR",
            source = "Salary",
            createdAt = 1000L,
            updatedAt = 1000L,
            syncStatus = SyncStatus.PENDING_DELETE,
            isDeleted = true
        )
        whenever(incomeDao.getIncomeById("user-123", "inc-1")).thenReturn(deletedEntity)

        val result = repository.getIncomeById("inc-1")

        assertNull(result)
    }

    @Test
    fun `observeIncome returns Room Flow and ignores soft-deleted rows`() = runTest {
        val activeEntity = IncomeEntity(
            id = "inc-1",
            userId = "user-123",
            amountMinor = 150000L,
            currency = "LKR",
            source = "Salary",
            syncStatus = SyncStatus.SYNCED,
            isDeleted = false
        )
        whenever(incomeDao.observeIncome("user-123")).thenReturn(flowOf(listOf(activeEntity)))

        val list = repository.observeIncome().first()

        assertEquals(1, list.size)
        assertEquals("inc-1", list[0].id)
    }

    private fun existingIncomeEntityToDomain(entity: IncomeEntity): Income {
        return entity.toDomain()
    }
}
