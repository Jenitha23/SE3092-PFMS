package com.pfms.app.data.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class SyncIssueStoreTest {
    @Test fun failuresAreScopedToTheirUserAndDoNotExposeDetails() = runTest {
        val store = SyncIssueStore()
        store.failed("a", "income-sync", IOException("Firebase users/a/private"))
        assertNull(store.observe("b").first())
        val problem = store.observe("a").first()!!
        assertTrue(problem.canRetry)
        assertFalse(problem.message.contains("Firebase"))
    }

    @Test fun recoveringOneFeatureDoesNotClearAnotherFailure() = runTest {
        val store = SyncIssueStore()
        store.failed("a", "income-sync", IOException())
        store.failed("a", "expense-sync", IOException())
        store.recovered("a", "income-sync")
        assertNotNull(store.observe("a").first())
        store.recovered("a", "expense-sync")
        assertNull(store.observe("a").first())
    }
}
