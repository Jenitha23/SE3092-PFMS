package com.pfms.app.data.sync

/** FR-35: UTC update millis, then deletion, then durable operation ID break equal-time ties. */
data class SyncVersion(val updatedAt: Long, val revision: String = "", val deleted: Boolean = false) : Comparable<SyncVersion> {
    override fun compareTo(other: SyncVersion): Int =
        compareValuesBy(this, other, SyncVersion::updatedAt, SyncVersion::deleted, SyncVersion::revision)

    companion object {
        fun nextTimestamp(previous: Long = 0L, requested: Long = 0L): Long =
            maxOf(System.currentTimeMillis(), requested, if (previous < Long.MAX_VALUE) previous + 1 else previous)
    }
}

data class RemoteDeletion(val id: String, val version: SyncVersion)
data class FinancialSyncResult<T>(val row: T?, val version: SyncVersion)
