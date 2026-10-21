package com.pfms.app.domain.repository

import com.pfms.app.domain.model.OfflineSyncState
import kotlinx.coroutines.flow.StateFlow

interface SyncStatusRepository {
    val status: StateFlow<OfflineSyncState>
    fun retry() {}
}
