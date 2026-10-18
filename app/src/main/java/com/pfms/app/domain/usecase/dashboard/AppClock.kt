package com.pfms.app.domain.usecase.dashboard

import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * NFR-17: months are always defined in Asia/Colombo so a late-night entry lands in the right
 * month regardless of the device time zone.
 */
object AppClock {
    val zone: ZoneId = ZoneId.of("Asia/Colombo")

    fun today(clock: Clock = Clock.system(zone)): LocalDate = LocalDate.now(clock.withZone(zone))

    fun currentMonth(clock: Clock = Clock.system(zone)): YearMonth = YearMonth.from(today(clock))
}
