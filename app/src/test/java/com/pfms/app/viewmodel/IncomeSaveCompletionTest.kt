package com.pfms.app.viewmodel

import com.pfms.app.data.FakeAuthRepository
import com.pfms.app.data.FakeIncomeRepository
import com.pfms.app.domain.model.AuthUser
import com.pfms.app.domain.model.Income
import com.pfms.app.domain.repository.IncomeRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeSaveCompletionTest {
    private val dispatcher = StandardTestDispatcher()
    private val rows = FakeIncomeRepository()
    private val auth = FakeAuthRepository(AuthUser("a", "Test", "test@example.com", true))
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() = Dispatchers.resetMain()
    private fun createVm(repository: IncomeRepository = rows) = IncomeViewModel(repository, auth, mock())
    private fun IncomeViewModel.save() = addIncome("1234.56", "LKR", "Salary", "2026-10-25", "Offline salary")

    @Test fun completionIsPublishedAfterPersistenceAndCanOnlyBeClaimedOnce() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val repository = object : IncomeRepository by rows {
            override suspend fun addIncome(income: Income) {
                release.await()
                rows.addIncome(income)
            }
        }
        val vm = createVm(repository)
        vm.save()
        runCurrent()
        assertTrue(vm.uiState.value.isSaving)
        assertFalse(vm.consumeSaveSuccess())
        assertTrue(rows.incomes.value.isEmpty())
        release.complete(Unit)
        runCurrent()
        assertTrue(vm.uiState.value.saveSuccessful)
        assertEquals(1, rows.incomes.value.size)
        val firstSnapshot = vm.uiState.first()
        val secondSnapshot = vm.uiState.first()
        assertTrue(firstSnapshot.saveSuccessful)
        assertTrue(secondSnapshot.saveSuccessful)
        assertTrue(vm.consumeSaveSuccess())
        assertFalse(vm.consumeSaveSuccess())
        assertFalse(vm.uiState.first().saveSuccessful)
        assertEquals(1, vm.uiState.value.incomes.size)
    }

    @Test fun simultaneousClaimsOfOneCompletionHaveExactlyOneWinner() = runTest(dispatcher) {
        val vm = createVm()
        vm.save()
        runCurrent()
        val claims = withContext(Dispatchers.Default) {
            (1..20).map { async { vm.consumeSaveSuccess() } }.awaitAll()
        }
        assertEquals(1, claims.count { it })
        assertEquals(1, rows.incomes.value.size)
    }

    @Test fun rapidSaveAndSaveWhileSuccessAwaitsNavigationDoNotCreateDuplicates() = runTest(dispatcher) {
        val vm = createVm()
        vm.save(); vm.save()
        runCurrent()
        vm.save()
        runCurrent()
        assertEquals(1, rows.incomes.value.size)
        assertTrue(vm.consumeSaveSuccess())
        vm.save()
        runCurrent()
        assertEquals(2, rows.incomes.value.size)
        assertEquals(2, rows.incomes.value.map { it.id }.distinct().size)
        assertTrue(vm.consumeSaveSuccess())
        assertFalse(vm.consumeSaveSuccess())
    }

    @Test fun editCompletionIsAlsoConsumedOnceAndDoesNotInsertANewRecord() = runTest(dispatcher) {
        val vm = createVm()
        vm.save()
        runCurrent()
        assertTrue(vm.consumeSaveSuccess())
        val id = rows.incomes.value.single().id
        vm.updateIncome(id, "4321.00", "LKR", "Salary", "2026-10-25", "Edited")
        runCurrent()
        assertTrue(vm.consumeSaveSuccess())
        assertFalse(vm.consumeSaveSuccess())
        assertEquals(id, rows.incomes.value.single().id)
        assertEquals(432100L, vm.uiState.value.incomes.single().amountMinor)
    }

    @Test fun failedSaveDoesNotEmitCompletionAndRetryRetainsItsRecordId() = runTest(dispatcher) {
        var fail = true
        val ids = mutableListOf<String>()
        val repository = object : IncomeRepository by rows {
            override suspend fun addIncome(income: Income) {
                ids += income.id
                if (fail) throw java.io.IOException("Temporary write failure")
                rows.addIncome(income)
            }
        }
        val vm = createVm(repository)
        vm.save()
        runCurrent()
        assertFalse(vm.consumeSaveSuccess())
        assertNotNull(vm.uiState.value.errorMessage)
        fail = false
        vm.retry()
        runCurrent()
        assertTrue(vm.consumeSaveSuccess())
        assertEquals(2, ids.size)
        assertEquals(ids.first(), ids.last())
        assertEquals(1, rows.incomes.value.size)
    }

    @Test fun newViewModelObservesPersistedIncomeWithoutReplayingSaveCompletion() = runTest(dispatcher) {
        val vm = createVm()
        vm.save()
        runCurrent()
        val restarted = createVm()
        runCurrent()
        assertEquals(123456L, restarted.uiState.value.incomes.single().amountMinor)
        assertFalse(restarted.consumeSaveSuccess())
    }
}
