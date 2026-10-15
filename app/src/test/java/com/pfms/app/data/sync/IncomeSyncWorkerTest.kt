package com.pfms.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IncomeSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var syncCoordinator: PendingSyncCoordinator
    private lateinit var worker: IncomeSyncWorker

    @Before
    fun setup() {
        context = mock()
        workerParams = mock()
        syncCoordinator = mock()

        worker = IncomeSyncWorker(
            appContext = context,
            workerParams = workerParams,
            syncCoordinator = syncCoordinator
        )
    }

    @Test
    fun `doWork returns Result success when syncCoordinator completes successfully`() = runTest {
        whenever(syncCoordinator.syncAllPending()).thenReturn(Result.success(Unit))

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(syncCoordinator).syncAllPending()
    }

    @Test
    fun `doWork returns Result retry when syncCoordinator fails`() = runTest {
        whenever(syncCoordinator.syncAllPending()).thenReturn(
            Result.failure(RuntimeException("Remote Firestore unavailable"))
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verify(syncCoordinator).syncAllPending()
    }

    @Test
    fun `doWork returns Result retry when syncCoordinator throws unexpected exception`() = runTest {
        whenever(syncCoordinator.syncAllPending()).thenThrow(
            RuntimeException("Unexpected background error")
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `work request configuration enforces NetworkType CONNECTED constraint and unique name`() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<IncomeSyncWorker>()
            .setConstraints(constraints)
            .build()

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals("pfms_pending_income_sync", IncomeSyncWorker.UNIQUE_WORK_NAME)
    }
}
