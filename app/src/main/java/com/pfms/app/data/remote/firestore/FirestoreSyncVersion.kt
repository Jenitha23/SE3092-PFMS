package com.pfms.app.data.remote.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.pfms.app.data.remote.firestore.mapper.parseTimestamp
import com.pfms.app.data.sync.SyncVersion

internal fun DocumentSnapshot.syncVersion() = SyncVersion(
    updatedAt = parseTimestamp(get("updatedAt")),
    revision = getString("syncRevision").orEmpty(),
    deleted = getBoolean("isDeleted") == true
)
