package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Importance
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
        importance: Importance = Importance.MEDIUM,
        preferredTimeMinutes: Int? = null
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
        importance = importance,
        preferredTimeMinutes = preferredTimeMinutes
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
    fun `missing multiple cycles compounds faster than linear`() {
        val s = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        val oneCycleLate = PriorityEngine.score(s, now = dayMillis(11))
        val twoCyclesLate = PriorityEngine.score(s, now = dayMillis(12))

        // A compounding (exponent > 1) curve means 2x the lateness is more than 2x the score.
        assertTrue(twoCyclesLate > oneCycleLate * 2)
    }

    @Test
    fun `one-time schedule scores higher than a daily schedule at identical lateness`() {
        val oneTime = schedule(Recurrence.ONE_TIME, nextDueDate = dayMillis(10))
        val daily = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10))
        val now = dayMillis(11)

        assertTrue(PriorityEngine.score(oneTime, now) > PriorityEngine.score(daily, now))
    }

    @Test
    fun `high importance outranks low importance at identical lateness`() {
        val low = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10), importance = Importance.LOW)
        val high = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10), importance = Importance.HIGH)
        val now = dayMillis(11)

        assertTrue(PriorityEngine.score(high, now) > PriorityEngine.score(low, now))
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
    fun `lateRatio is unaffected by importance`() {
        val low = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10), importance = Importance.LOW)
        val high = schedule(Recurrence.DAILY, nextDueDate = dayMillis(10), importance = Importance.HIGH)
        val now = dayMillis(11)

        assertEquals(PriorityEngine.lateRatio(low, now), PriorityEngine.lateRatio(high, now), 0.0001)
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
}
