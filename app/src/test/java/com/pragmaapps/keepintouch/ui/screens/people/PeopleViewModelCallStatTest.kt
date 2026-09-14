package com.pragmaapps.keepintouch.ui.screens.people

import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PeopleViewModelCallStatTest {

    private fun dayMillis(daysFromEpoch: Long): Long =
        LocalDate.ofEpochDay(daysFromEpoch).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun schedule(active: Boolean) = Schedule(
        id = 1,
        contactLookupKey = "key",
        displayNameSnapshot = "Mummy",
        normalizedNameSnapshot = "mummy",
        numberSnapshot = null,
        recurrence = Recurrence.WEEKLY,
        startDate = dayMillis(0),
        nextDueDate = dayMillis(0),
        active = active,
        note = null,
        createdAt = 0
    )

    private fun qualifiedEvent(postTime: Long) = CallEvent(
        id = 0,
        packageName = "com.whatsapp",
        notifKey = "key",
        statesSeen = "ONGOING",
        connected = true,
        isVideo = false,
        isGroupCall = false,
        direction = Direction.INCOMING,
        notificationName = "Mummy",
        postTime = postTime,
        ongoingWhen = null,
        ongoingPostedElapsedRealtime = null,
        endTime = null,
        inferredDurationMs = null,
        qualified = true,
        avatarHash = null,
        matchedScheduleId = 1,
        matchConfidence = null,
        rawJson = "{}"
    )

    @Test
    fun `active schedule reports the current month, even when it has zero calls so far`() {
        val (count, monthName) = callStatFor(schedule(active = true), emptyList(), now = dayMillis(20))
        assertEquals(0, count)
        assertEquals(LocalDate.ofEpochDay(20).month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH), monthName)
    }

    @Test
    fun `active schedule only counts calls from the start of the current month onward`() {
        val lastMonthCall = LocalDate.of(2026, 8, 15).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val thisMonthCall = LocalDate.of(2026, 9, 5).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 9, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val (count, _) = callStatFor(schedule(active = true), listOf(qualifiedEvent(lastMonthCall), qualifiedEvent(thisMonthCall)), now)
        assertEquals(1, count)
    }

    @Test
    fun `paused schedule with no calls ever reports no month`() {
        val (count, monthName) = callStatFor(schedule(active = false), emptyList(), now = dayMillis(20))
        assertEquals(0, count)
        assertEquals(null, monthName)
    }

    @Test
    fun `paused schedule reports the most recent month that actually had a call, not the current month`() {
        // Paused now (day 100, some month far in the future of the calls), but had 2 calls in an
        // earlier month and 1 in an even earlier one - should report the most recent of those,
        // not "0 this month".
        val earlierMonth = LocalDate.of(2026, 5, 10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recentMonthCall1 = LocalDate.of(2026, 7, 3).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recentMonthCall2 = LocalDate.of(2026, 7, 20).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 9, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val (count, monthName) = callStatFor(
            schedule(active = false),
            listOf(qualifiedEvent(earlierMonth), qualifiedEvent(recentMonthCall1), qualifiedEvent(recentMonthCall2)),
            now
        )
        assertEquals(2, count)
        assertEquals("July", monthName)
    }
}
