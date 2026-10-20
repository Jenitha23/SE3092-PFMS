package com.pfms.app.data.repository

import com.pfms.app.data.local.PfmsDatabase
import com.pfms.app.domain.model.OfflineSyncState
import com.pfms.app.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncStatusRepository @Inject constructor(
    db: PfmsDatabase, auth: AuthRepository, connectivity: ConnectivityObserver
) : SyncStatusRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = auth.currentUser.map { it?.uid?.takeIf(String::isNotBlank) }
        .distinctUntilChanged().flatMapLatest { uid ->
            // Clear the previous user's count immediately; queries are always user-scoped.
            if (uid == null) flowOf(0) else db.pendingChangesDao().observePendingCount(uid).onStart { emit(0) }
        }
    override val status = combine(connectivity.isOnline, pending) { online, count ->
        OfflineSyncState(online, count)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000, 0), OfflineSyncState(connectivity.isOnline.value))
}
