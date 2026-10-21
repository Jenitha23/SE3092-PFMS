package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.ReminderSchedule
import com.pfms.app.domain.model.ReminderType
import com.pfms.app.domain.usecase.dashboard.AppClock
import java.time.*
import java.time.temporal.TemporalAdjusters

/** Scheduling only. Financial eligibility belongs to the individual reminder rules. */
object ReminderSchedulePolicy {
    fun nextAt(now: Instant, type: ReminderType, schedule: ReminderSchedule): Instant {
        val local = now.atZone(AppClock.zone)
        var date = local.toLocalDate()
        if (type == ReminderType.WEEKLY_SUMMARY) {
            date = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(schedule.dayOfWeek)))
        }
        var target = date.atTime(LocalTime.ofSecondOfDay(schedule.minuteOfDay * 60L)).atZone(AppClock.zone).toInstant()
        if (target.isBefore(now)) {
            date = if (type == ReminderType.WEEKLY_SUMMARY) date.plusWeeks(1) else date.plusDays(1)
            target = date.atTime(LocalTime.ofSecondOfDay(schedule.minuteOfDay * 60L)).atZone(AppClock.zone).toInstant()
        }
        return target
    }

    /** No delivery before today's (or this ISO week's) configured opportunity. */
    fun deliverySlot(now: Instant, type: ReminderType, schedule: ReminderSchedule): String? {
        var date = now.atZone(AppClock.zone).toLocalDate()
        if (type == ReminderType.WEEKLY_SUMMARY) {
            date = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(schedule.dayOfWeek - 1L)
        }
        val target = date.atTime(LocalTime.ofSecondOfDay(schedule.minuteOfDay * 60L)).atZone(AppClock.zone).toInstant()
        return date.toString().takeUnless { now.isBefore(target) }
    }
}
