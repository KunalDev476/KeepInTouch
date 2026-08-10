package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import kotlin.math.pow

/**
 * Computes a single sortable "how urgently does this need a call" score, used to order the Today
 * list instead of alphabetically. Pure/JVM-only, no persistence, no background job - recomputed
 * fresh from [Schedule.nextDueDate] + now every time it's read, so it's always correct even if the
 * app process was dead for days (see the background-execution discussion this was designed
 * against: nothing needs to have "kept running" for the score to be right).
 *
 * Core idea: normalize lateness to the schedule's own cadence before comparing schedules, so a
 * DAILY schedule a day late (fully missed one cycle) and a WEEKLY schedule a day late (barely
 * dented) aren't judged on the same absolute clock. ONE_TIME has no recovery cycle to be forgiving
 * against, so it's compared against a short reference window and penalized further.
 */
object PriorityEngine {

    private const val LATE_EXPONENT = 1.4
    private const val ONE_TIME_SEVERITY_MULTIPLIER = 1.5

    /** [lateRatio] value at which a MISSED schedule's escalation visuals max out - see escalationFraction(). */
    private const val ESCALATION_CAP = 2.0

    private const val MINUTES_TO_MILLIS = 60_000L
    private val ONE_TIME_CADENCE_MS = DAY_MS
    private val DAILY_CADENCE_MS = DAY_MS
    private val WEEKLY_CADENCE_MS = DAY_MS * 7

    /** The moment this schedule is actually due, including its time-of-day if one was set. */
    fun dueInstantMillis(schedule: Schedule): Long {
        val startOfDueDay = DateMath.startOfDayMillis(schedule.nextDueDate)
        val timeOffsetMs = (schedule.preferredTimeMinutes ?: 0) * MINUTES_TO_MILLIS
        return startOfDueDay + timeOffsetMs
    }

    /**
     * How overdue [schedule] is right now, normalized to its own cadence (0 = not yet due, 1 =
     * exactly one full cadence late, unbounded beyond that). Deliberately importance-free - this
     * is the "how late" half of [score] on its own, factored out so anything that wants lateness
     * without importance mixed in (e.g. escalating a missed-schedule icon's color) can reuse the
     * exact same math instead of re-deriving it.
     */
    fun lateRatio(schedule: Schedule, now: Long): Double {
        val cadenceMs = when (schedule.recurrence) {
            Recurrence.ONE_TIME -> ONE_TIME_CADENCE_MS
            Recurrence.DAILY -> DAILY_CADENCE_MS
            Recurrence.WEEKLY -> WEEKLY_CADENCE_MS
        }
        val overdueMs = (now - dueInstantMillis(schedule)).coerceAtLeast(0)
        return overdueMs.toDouble() / cadenceMs
    }

    /**
     * A 0..1 escalation value for a MISSED schedule's visuals (see ScheduleStatusIcon), fully
     * ramped once overdue by ~[ESCALATION_CAP] cadences - 2 days late for a daily schedule, 2
     * weeks for a weekly one, proportional rather than an absolute day count, same philosophy as
     * [lateRatio]/[score]. Importance-free, same as [lateRatio].
     */
    fun escalationFraction(schedule: Schedule, now: Long): Float =
        (lateRatio(schedule, now) / ESCALATION_CAP).coerceIn(0.0, 1.0).toFloat()

    /** Higher = more urgent. Not bounded - a very overdue schedule can score arbitrarily high, which is fine for a sort-only key. */
    fun score(schedule: Schedule, now: Long): Double {
        var urgency = lateRatio(schedule, now).pow(LATE_EXPONENT)
        if (schedule.recurrence == Recurrence.ONE_TIME) {
            urgency *= ONE_TIME_SEVERITY_MULTIPLIER
        }

        return schedule.importance.multiplier * urgency
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000
