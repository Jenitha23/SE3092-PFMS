package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.*
import com.pfms.app.domain.usecase.dashboard.AppClock
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderSchedulePolicyTest {
    private fun instant(date: String, hour: Int, minute: Int = 0) = LocalDate.parse(date).atTime(hour, minute)
        .atZone(AppClock.zone).toInstant()
    private val daily = ReminderSchedule(true, 20 * 60)
    @Test fun allSevenTypesHaveIndependentStableKeysAndDefaults() {
        val settings = UserReminderSettings.defaults("a")
        assertEquals(7, settings.schedules.size)
        assertEquals(7, ReminderType.entries.map { it.key }.toSet().size)
        assertTrue(settings[ReminderType.DUE_RECURRING].enabled)
        assertTrue(settings[ReminderType.SUBSCRIPTION_REVIEW].enabled)
        assertFalse(settings[ReminderType.DAILY_LOG].enabled)
        assertEquals(2, ReminderType.entries.count { it.available })
    }
    @Test fun dailyBeforeTimeSchedulesToday() {
        assertEquals(instant("2026-10-20", 20), ReminderSchedulePolicy.nextAt(instant("2026-10-20", 19), ReminderType.DAILY_LOG, daily))
    }
    @Test fun exactlyConfiguredTimeIsEligible() {
        val now = instant("2026-10-20", 20)
        assertEquals(now, ReminderSchedulePolicy.nextAt(now, ReminderType.DAILY_LOG, daily))
        assertEquals("2026-10-20", ReminderSchedulePolicy.deliverySlot(now, ReminderType.DAILY_LOG, daily))
    }
    @Test fun afterTimeSchedulesTomorrowWithoutAnImmediateBacklogNotification() {
        assertEquals(instant("2026-10-21", 20), ReminderSchedulePolicy.nextAt(instant("2026-10-20", 21), ReminderType.DAILY_LOG, daily))
    }
    @Test fun aWorkerCannotDeliverBeforeTheConfiguredTime() {
        assertNull(ReminderSchedulePolicy.deliverySlot(instant("2026-10-20", 19, 59), ReminderType.DAILY_LOG, daily))
    }
    @Test fun delayedWorkerUsesTodaysSlotRatherThanCatchingUpEveryMissedDay() {
        assertEquals("2026-10-23", ReminderSchedulePolicy.deliverySlot(instant("2026-10-23", 23), ReminderType.DAILY_LOG, daily))
    }
    @Test fun weeklyDayAndTimeScheduleCorrectlyAcrossWeekBoundaries() {
        val weekly = ReminderSchedule(true, 18 * 60, 7)
        assertEquals(instant("2026-10-25", 18), ReminderSchedulePolicy.nextAt(instant("2026-10-20", 12), ReminderType.WEEKLY_SUMMARY, weekly))
        assertNull(ReminderSchedulePolicy.deliverySlot(instant("2026-10-25", 17, 59), ReminderType.WEEKLY_SUMMARY, weekly))
        assertEquals("2026-10-25", ReminderSchedulePolicy.deliverySlot(instant("2026-10-25", 18), ReminderType.WEEKLY_SUMMARY, weekly))
        assertEquals(instant("2026-11-01", 18), ReminderSchedulePolicy.nextAt(instant("2026-10-25", 19), ReminderType.WEEKLY_SUMMARY, weekly))
    }
    @Test fun monthAndYearRolloverAndMidnightAreSafe() {
        assertEquals(instant("2027-01-01", 0), ReminderSchedulePolicy.nextAt(instant("2026-12-31", 23, 59), ReminderType.DAILY_LOG,
            daily.copy(minuteOfDay = 0)))
    }
    @Test fun calendarTimeUsesColomboIndependentlyOfDeviceZone() {
        val now = Instant.parse("2026-10-20T14:29:59Z")
        assertNull(ReminderSchedulePolicy.deliverySlot(now, ReminderType.DAILY_LOG, daily))
        assertEquals(Instant.parse("2026-10-20T14:30:00Z"), ReminderSchedulePolicy.nextAt(now, ReminderType.DAILY_LOG, daily))
    }
    @Test fun malformedWorkerTypesAndInvalidTimeValuesAreRejected() {
        assertNull(ReminderType.fromKey(null)); assertNull(ReminderType.fromKey("not-a-reminder"))
        for (minute in listOf(-1, 1440)) assertThrows(IllegalArgumentException::class.java) { ReminderSchedule(minuteOfDay = minute) }
        assertThrows(IllegalArgumentException::class.java) { ReminderSchedule(dayOfWeek = 0) }
    }
}
