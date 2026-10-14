package com.pfms.app.data.local.entity

/**
 * Represents the offline sync state of a locally cached record.
 */
enum class SyncStatus {
    SYNCED,
    PENDING_INSERT,
    PENDING_UPDATE,
    PENDING_DELETE
}
