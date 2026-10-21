package com.pfms.app.data.sync

import com.pfms.app.data.error.ErrorAction
import com.pfms.app.data.error.OperationError
import com.pfms.app.data.error.OperationErrors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Transient presentation status only. Room and the existing workers still own all pending writes. */
@Singleton
class SyncIssueStore @Inject constructor() {
    private val issues = MutableStateFlow<Map<Pair<String, String>, OperationError>>(emptyMap())
    fun observe(userId: String) = issues.map { items -> items.entries.lastOrNull { it.key.first == userId }?.value }
    fun failed(userId: String, source: String, error: Throwable) {
        val message = OperationErrors.present(error, ErrorAction.LOAD)
        issues.update { it + ((userId to source) to message) }
    }
    fun recovered(userId: String, source: String) { issues.update { it - (userId to source) } }
}
