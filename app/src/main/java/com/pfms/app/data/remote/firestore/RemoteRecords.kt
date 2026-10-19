package com.pfms.app.data.remote.firestore

data class RemoteRecords<T>(val rows: List<T>, val authoritative: Boolean)
