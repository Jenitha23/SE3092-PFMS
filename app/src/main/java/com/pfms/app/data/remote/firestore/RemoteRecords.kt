package com.pfms.app.data.remote.firestore
import com.pfms.app.data.sync.RemoteDeletion
import com.pfms.app.data.sync.SyncVersion

data class RemoteRecords<T>(val rows: List<T>, val authoritative: Boolean,
    val versions: Map<String, SyncVersion> = emptyMap(), val deletions: List<RemoteDeletion> = emptyList())
