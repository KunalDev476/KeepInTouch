package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurrenceEngineTest {

    // Mirrors DateMath's own local-midnight logic exactly, so these fixtures stay correct
    // regardless of which timezone the test happens to run in.
    private fun dayMillis(daysFromEpoch: Long): Long =
        LocalDate.ofEpochDay(daysFromEpoch).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun schedule(recurrence: Recurrence, nextDueDate: Long, active: Boolean = true) = Schedule(
        id = 1,
        contactLookupKey = "key",
        displayNameSnapshot = "Mummy",
        normalizedNameSnapshot = "mummy",
        numberSnapshot = null,
        recurrence = recurrence,
        startDate = nextDueDate,
        nextDueDate = nextDueDate,
        active = active,
        note = null,
        createdAt = 0
    )

    @Test
    fun `one-time schedule becomes inactive after completion and keeps its due date`() {
        val original = schedule(Recurrence.ONE_TIME, nextDueDate = dayMillis(10))
        val advanced = RecurrenceEngine.advanceAfterCompletion(original, completedAt = dayMillis(10))
        assertFalse(advanced.active)
        assertEquals(original.nextDueDate, advanced.nextDueDate)
    }

    @Test
    fun `daily schedule advances to the day after completion`() {
        val original = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        val advanced = RecurrenceEngine.advanceAfterCompletion(original, completedAt = dayMillis(10))
        assertEquals(dayMillis(11), advanced.nextDueDate)
        assertTrue(advanced.active)
    }

    @Test
    fun `weekly schedule advances 7 days after completion`() {
        val original = schedule(Recurrence.WEEKLY, nextDueDate = dayMillis(10))
        val advanced = RecurrenceEngine.advanceAfterCompletion(original, completedAt = dayMillis(10))
        assertEquals(dayMillis(17), advanced.nextDueDate)
    }

    @Test
    fun `an overdue schedule advances from the completion date, not the stale due date`() {
        // Was due on day 10, actually fulfilled on day 15 (5 days late).
        val original = schedule(Recurrence.WEEKLY, nextDueDate = dayMillis(10))
        val advanced = RecurrenceEngine.advanceAfterCompletion(original, completedAt = dayMillis(15))
        assertEquals(dayMillis(22), advanced.nextDueDate) // 15 + 7, not 10 + 7
    }

    @Test
    fun `isDue is true when nextDueDate is today`() {
        val today = dayMillis(20)
        val s = schedule(Recurrence.DAILY, nextDueDate = today)
        assertTrue(RecurrenceEngine.isDue(s, now = today))
    }

    @Test
    fun `isDue is true when overdue`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(5))
        assertTrue(RecurrenceEngine.isDue(s, now = dayMillis(20)))
    }

    @Test
    fun `isDue is false when due date is in the future`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(25))
        assertFalse(RecurrenceEngine.isDue(s, now = dayMillis(20)))
    }

    @Test
    fun `isDue is false for an inactive schedule even if the date has passed`() {
        val s = schedule(Recurrence.ONE_TIME, nextDueDate = dayMillis(5), active = false)
        assertFalse(RecurrenceEngine.isDue(s, now = dayMillis(20)))
    }

    @Test
    fun `status is DONE when nextDueDate is in the future`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(25))
        assertEquals(com.pragmaapps.keepintouch.domain.model.ScheduleStatus.DONE, RecurrenceEngine.status(s, now = dayMillis(20)))
    }

    @Test
    fun `status is PENDING when due today`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(20))
        assertEquals(com.pragmaapps.keepintouch.domain.model.ScheduleStatus.PENDING, RecurrenceEngine.status(s, now = dayMillis(20)))
    }

    @Test
    fun `status is MISSED when overdue`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(5))
        assertEquals(com.pragmaapps.keepintouch.domain.model.ScheduleStatus.MISSED, RecurrenceEngine.status(s, now = dayMillis(20)))
    }

    @Test
    fun `status is DONE for an inactive schedule even if overdue`() {
        val s = schedule(Recurrence.ONE_TIME, nextDueDate = dayMillis(5), active = false)
        assertEquals(com.pragmaapps.keepintouch.domain.model.ScheduleStatus.DONE, RecurrenceEngine.status(s, now = dayMillis(20)))
    }

    private fun scheduleWithStart(
        recurrence: Recurrence,
        startDate: Long,
        isImportant: Boolean = false,
        preferredTimeMinutes: Int? = null
    ) = Schedule(
        id = 1,
        contactLookupKey = "key",
        displayNameSnapshot = "Mummy",
        normalizedNameSnapshot = "mummy",
        numberSnapshot = null,
        recurrence = recurrence,
        startDate = startDate,
        nextDueDate = startDate,
        active = true,
        note = null,
        createdAt = 0,
        preferredTimeMinutes = preferredTimeMinutes,
        isImportant = isImportant
    )

    @Test
    fun `missedCount is 0 when every expected daily occurrence has a completion`() {
        // Created on day 0, today is day 4, not important/timed so today doesn't count yet ->
        // 4 expected occurrences (days 0,1,2,3).
        val s = scheduleWithStart(Recurrence.DAILY, startDate = dayMillis(0))
        assertEquals(0, RecurrenceEngine.missedCount(s, completionCount = 4, now = dayMillis(4)))
    }

    @Test
    fun `missedCount counts the gap between expected and actual daily completions, excluding today`() {
        // Not important/timed, so today (day 4) isn't counted as expected yet - only days 0-3.
        val s = scheduleWithStart(Recurrence.DAILY, startDate = dayMillis(0))
        assertEquals(2, RecurrenceEngine.missedCount(s, completionCount = 2, now = dayMillis(4)))
    }

    @Test
    fun `missedCount counts today for an important, timed schedule once its due time has passed`() {
        val s = scheduleWithStart(Recurrence.DAILY, startDate = dayMillis(0), isImportant = true, preferredTimeMinutes = 600) // 10:00am
        val afterDueTimeToday = dayMillis(4) + 601 * 60_000L // 10:01am on day 4
        assertEquals(3, RecurrenceEngine.missedCount(s, completionCount = 2, now = afterDueTimeToday))
    }

    @Test
    fun `missedCount does not count today for an important, timed schedule before its due time`() {
        val s = scheduleWithStart(Recurrence.DAILY, startDate = dayMillis(0), isImportant = true, preferredTimeMinutes = 600) // 10:00am
        val beforeDueTimeToday = dayMillis(4) + 599 * 60_000L // 9:59am on day 4
        assertEquals(2, RecurrenceEngine.missedCount(s, completionCount = 2, now = beforeDueTimeToday))
    }

    @Test
    fun `missedCount for weekly divides elapsed days by 7, excluding today's cycle if not yet due`() {
        // 14 days elapsed lands exactly on a new cycle's start day (day 14) - not important/timed,
        // so that cycle doesn't count yet -> 2 expected cycles (day 0, day 7).
        val s = scheduleWithStart(Recurrence.WEEKLY, startDate = dayMillis(0))
        assertEquals(0, RecurrenceEngine.missedCount(s, completionCount = 2, now = dayMillis(14)))
    }

    @Test
    fun `missedCount for weekly counts a cycle whose start day has already passed`() {
        // 16 days elapsed - the current cycle (starting day 14) began 2 days ago, so it's
        // unconditionally expected regardless of importance/time -> 3 expected cycles.
        val s = scheduleWithStart(Recurrence.WEEKLY, startDate = dayMillis(0))
        assertEquals(1, RecurrenceEngine.missedCount(s, completionCount = 2, now = dayMillis(16)))
    }

    @Test
    fun `missedCount is 0 before the schedule has even started`() {
        val s = scheduleWithStart(Recurrence.DAILY, startDate = dayMillis(10))
        assertEquals(0, RecurrenceEngine.missedCount(s, completionCount = 0, now = dayMillis(5)))
    }

    @Test
    fun `daysBetween is DST-safe calendar-day arithmetic, not a raw millis divide`() {
        assertEquals(10L, DateMath.daysBetween(dayMillis(0), dayMillis(10)))
    }
}
