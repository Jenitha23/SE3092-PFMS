package com.pfms.app.data.sync

import org.junit.Assert.*
import org.junit.Test

class SyncVersionTest {
    @Test fun timestampDeletionAndRevisionProduceADeterministicTotalOrder() {
        val versions = listOf(SyncVersion(1, "z"), SyncVersion(2, "a"), SyncVersion(2, "z"), SyncVersion(2, "a", true))
        for (i in versions.indices) for (j in versions.indices) {
            assertEquals(i.compareTo(j), versions[i].compareTo(versions[j]).coerceIn(-1, 1))
        }
    }
    @Test fun localMutationTimeAdvancesEvenWhenWallClockMovesBackwards() {
        val previous = System.currentTimeMillis() + 60_000
        assertEquals(previous + 1, SyncVersion.nextTimestamp(previous, requested = 1))
    }
    @Test fun retriesKeepExactlyTheSameVersion() {
        val original = SyncVersion(1234, "persisted-operation", true)
        assertEquals(0, original.compareTo(original.copy()))
    }
}
