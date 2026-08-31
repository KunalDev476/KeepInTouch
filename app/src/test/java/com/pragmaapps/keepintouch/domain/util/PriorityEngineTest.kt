package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PriorityEngineTest {

    private fun dayMillis(daysFromEpoch: Long): Long =
        LocalDate.ofEpochDay(daysFromEpoch).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun schedule(
        recurrence: Recurrence,
        nextDueDate: Long,
        preferredTimeMinutes: Int? = null,
        comfortableTimeEndMinutes: Int? = null
    ) = Schedule(
        id = 1,
        contactLookupKey = "key",
        displayNameSnapshot = "Mummy",
        normalizedNameSnapshot = "mummy",
        numberSnapshot = null,
        recurrence = recurrence,
        startDate = nextDueDate,
        nextDueDate = nextDueDate,
        active = true,
        note = null,
        createdAt = 0,
        preferredTimeMinutes = preferredTimeMinutes,
        comfortableTimeEndMinutes = comfortableTimeEndMinutes
    )

    @Test
    fun `not yet due scores zero`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(20))
        assertEquals(0.0, PriorityEngine.score(s, now = dayMillis(10)), 0.0001)
    }

    @Test
    fun `daily schedule one full cadence late scores higher than weekly schedule equally late in absolute time`() {
        val daily = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        val weekly = schedule(Recurrence.WEEKLY, nextDueDate = dayMillis(10))
        val now = dayMillis(11) // 1 day late for both, in absolute terms

        val dailyScore = PriorityEngine.score(daily, now)
        val weeklyScore = PriorityEngine.score(weekly, now)

        // Daily has used up a full cycle's worth of slack; weekly has barely used 1/7th of its cadence.
        assertTrue("expected daily ($dailyScore) > weekly ($weeklyScore)", dailyScore > weeklyScore)
    }

    @Test
    fun `one full cadence late scores exactly 100 percent`() {
        val daily = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        assertEquals(100.0, PriorityEngine.score(daily, now = dayMillis(11)), 0.0001)

        val weekly = schedule(Recurrence.WEEKLY, nextDueDate = dayMillis(10))
        assertEquals(100.0, PriorityEngine.score(weekly, now = dayMillis(17)), 0.0001)
    }

    @Test
    fun `score scales linearly with cycles missed`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        val oneCycleLate = PriorityEngine.score(s, now = dayMillis(11))
        val twoCyclesLate = PriorityEngine.score(s, now = dayMillis(12))

        assertEquals(oneCycleLate * 2, twoCyclesLate, 0.0001)
    }

    @Test
    fun `one-time and daily schedules score identically at identical lateness`() {
        val oneTime = schedule(Recurrence.ONE_TIME, nextDueDate = dayMillis(10))
        val daily = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        val now = dayMillis(11)

        assertEquals(PriorityEngine.score(daily, now), PriorityEngine.score(oneTime, now), 0.0001)
    }

    @Test
    fun `a preferred time later in the same day delays when lateness starts accruing`() {
        val withTime = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10), preferredTimeMinutes = 10 * 60) // 10am
        val noTime = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10)) // effectively due at midnight

        val at9am = dayMillis(10) + 9 * 60 * 60 * 1000L
        assertEquals(0.0, PriorityEngine.score(withTime, now = at9am), 0.0001) // not due yet at 9am
        assertTrue(PriorityEngine.score(noTime, now = at9am) > 0.0) // already accruing since midnight
    }

    @Test
    fun `lateRatio is zero when not yet due and grows with lateness`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        assertEquals(0.0, PriorityEngine.lateRatio(s, now = dayMillis(5)), 0.0001)

        val oneCycleLate = PriorityEngine.lateRatio(s, now = dayMillis(11))
        val twoCyclesLate = PriorityEngine.lateRatio(s, now = dayMillis(12))
        assertTrue(twoCyclesLate > oneCycleLate)
    }

    @Test
    fun `escalationFraction is coerced between 0 and 1 and reaches 1 once fully escalated`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        assertEquals(0f, PriorityEngine.escalationFraction(s, now = dayMillis(5)), 0.0001f)

        val halfway = PriorityEngine.escalationFraction(s, now = dayMillis(11)) // 1 cadence late, cap is 2
        assertTrue(halfway > 0f && halfway < 1f)

        val veryLate = PriorityEngine.escalationFraction(s, now = dayMillis(50))
        assertEquals(1f, veryLate, 0.0001f)
    }

    @Test
    fun `windowEndInstantMillis is null without a comfortable end time`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10), preferredTimeMinutes = 10 * 60)
        assertEquals(null, PriorityEngine.windowEndInstantMillis(s))
    }

    @Test
    fun `windowEndInstantMillis lands on the due day at the configured minute`() {
        val s = schedule(
            Recurrence.DAILY,
            nextDueDate = dayMillis(10),
            preferredTimeMinutes = 10 * 60, // 10am
            comfortableTimeEndMinutes = 11 * 60 // 11am
        )
        val expected = dayMillis(10) + 11 * 60 * 60 * 1000L
        assertEquals(expected, PriorityEngine.windowEndInstantMillis(s))
    }
}
