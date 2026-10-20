package com.pfms.app.data.repository

import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.domain.model.OfflineSyncState
import com.pfms.app.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.pfms.app.data.sync.SyncIssueStore
import com.pfms.app.data.sync.IncomeSyncWorker
import com.pfms.app.data.sync.ExpenseSyncWorker
import androidx.work.ExistingWorkPolicy

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncStatusRepository @Inject constructor(
    db: PfmsDatabase, private val auth: AuthRepository, private val connectivity: ConnectivityObserver
) : SyncStatusRepository {
    @Inject lateinit var issues: SyncIssueStore
    @Inject @ApplicationContext lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = auth.currentUser.map { it?.uid?.takeIf(String::isNotBlank) }
        .distinctUntilChanged().flatMapLatest { uid ->
            // Clear the previous user's count immediately; queries are always user-scoped.
            if (uid == null) flowOf(0) else db.pendingChangesDao().observePendingCount(uid).onStart { emit(0) }
        }
    override val status by lazy {
        val errors = auth.currentUser.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null || !::issues.isInitialized) flowOf(null) else issues.observe(uid).onStart { emit(null) }
        }
        combine(connectivity.isOnline, pending, errors) { online, count, error ->
            OfflineSyncState(online, count, error?.message, error?.canRetry == true)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000, 0), OfflineSyncState(connectivity.isOnline.value))
    }
    override fun retry() {
        if (::context.isInitialized) {
            // A user-requested retry should not wait behind a job's previous backoff delay.
            IncomeSyncWorker.enqueue(context, ExistingWorkPolicy.REPLACE)
            ExpenseSyncWorker.enqueue(context, ExistingWorkPolicy.REPLACE)
        }
    }
}
