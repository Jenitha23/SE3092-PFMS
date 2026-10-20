package com.pfms.app.domain.model

data class OfflineSyncState(val isOnline: Boolean = true, val pendingChanges: Int = 0)
