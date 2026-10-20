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
        f.change(ReminderType.DAILY_LOG, true, 20 * 60)
        assertTrue(f.store.read(f.uid)[ReminderType.DAILY_LOG].enabled)
        val delivery = ReminderDelivery(f.store, f.auth, notifier, f.source, f.clock)
        assertEquals(ReminderRunResult.COMPLETE, delivery.run(f.uid, ReminderType.DUE_RECURRING.key,
            f.store.read(f.uid)[ReminderType.DUE_RECURRING].revision, 1))
        assertEquals(0, f.source.calls)
    }
}
