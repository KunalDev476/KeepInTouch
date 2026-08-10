package com.pragmaapps.keepintouch.domain.util

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Small date-math helpers shared by recurrence and due-date logic. Pure/JVM-only (java.time, not android.*). */
object DateMath {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun startOfDayMillis(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    fun plusDays(epochMillis: Long, days: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().plusDays(days)
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** Whole calendar days between two instants - DST-safe (unlike dividing a millis difference by 86400000). */
    fun daysBetween(fromEpochMillis: Long, toEpochMillis: Long): Long =
        ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(fromEpochMillis).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(toEpochMillis).atZone(zone).toLocalDate()
        )

    fun formatDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    /** Wall-clock time of [epochMillis] in the device's zone, as a 12-hour "h:mm a" label. */
    fun formatTime(epochMillis: Long): String {
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return formatTimeOfDay(time.hour * 60 + time.minute)
    }

    /** minutesOfDay = minutes since midnight (e.g. 10:00am = 600) -> a 12-hour "h:mm a" label. */
    fun formatTimeOfDay(minutesOfDay: Int): String {
        val hour24 = minutesOfDay / 60
        val minute = minutesOfDay % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        return "%d:%02d %s".format(hour12, minute, amPm)
    }
}
