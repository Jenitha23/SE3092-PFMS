package com.pfms.app

import androidx.test.platform.app.InstrumentationRegistry
import com.pfms.app.data.reminder.*
import com.pfms.app.domain.model.ReminderType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ReminderPermissionDeniedTest {
    @Test fun androidPermissionDenialSkipsDeliveryAndStillAllowsSavingSettings() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("fr38PermissionDenied") == "true")
        val f = ReminderFixture()
        val notifier = AndroidReminderNotifier(f.context)
        assertFalse(notifier.access().allowed)
        val delivery = ReminderDelivery(f.store, f.auth, notifier, f.source, f.clock)
        for (type in ReminderType.entries) {
            f.store.locked { f.store.update(f.uid, type, true, 0, 2) }
            assertTrue(f.store.read(f.uid)[type].enabled)
            assertEquals(ReminderRunResult.COMPLETE, delivery.run(f.uid, type.key,
                f.store.read(f.uid)[type].revision, 1))
        }
        assertEquals(0, f.source.calls)
    }
}
