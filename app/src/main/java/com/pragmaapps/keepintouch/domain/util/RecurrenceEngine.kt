package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.model.ScheduleStatus

/**
 * Recurrence math, kept pure and separate from persistence. Rules:
 * - ONE_TIME: due on [Schedule.nextDueDate], then becomes inactive on fulfillment. No further occurrences.
 * - DAILY: due every day; fulfilling advances nextDueDate to the day after the completion date.
 * - WEEKLY: fulfilling advances nextDueDate 7 days after the completion date.
 * - BIWEEKLY: fulfilling advances nextDueDate 14 days after the completion date.
 * - MONTHLY: fulfilling advances nextDueDate 1 calendar month after the completion date (see DateMath.plusMonths).
 *
 * Advancing from the completion date (not the previous nextDueDate) means an overdue schedule
 * that gets fulfilled late doesn't stay perpetually overdue - it resets forward from "now."
 */
object RecurrenceEngine {

    fun advanceAfterCompletion(schedule: Schedule, completedAt: Long): Schedule = when (schedule.recurrence) {
        Recurrence.ONE_TIME -> schedule.copy(active = false)
        Recurrence.DAILY -> schedule.copy(nextDueDate = DateMath.plusDays(completedAt, 1))
        Recurrence.WEEKLY -> schedule.copy(nextDueDate = DateMath.plusDays(completedAt, 7))
        Recurrence.BIWEEKLY -> schedule.copy(nextDueDate = DateMath.plusDays(completedAt, 14))
        Recurrence.MONTHLY -> schedule.copy(nextDueDate = DateMath.plusMonths(completedAt, 1))
    }

    /**
     * The natural first due date for a brand-new schedule of this recurrence, projected forward
     * from [from] (normally "now") instead of defaulting to [from]'s own day - mirrors
     * [advanceAfterCompletion]'s own interval per recurrence, treating creation as if the cadence
     * had just restarted. ONE_TIME is the one exception: a single occurrence has no cadence to
     * project forward, so it stays on [from]'s own day until the user picks a specific date.
     */
    fun defaultNextDueDate(recurrence: Recurrence, from: Long): Long = when (recurrence) {
        Recurrence.ONE_TIME -> DateMath.startOfDayMillis(from)
        Recurrence.DAILY -> DateMath.plusDays(from, 1)
        Recurrence.WEEKLY -> DateMath.plusDays(from, 7)
        Recurrence.BIWEEKLY -> DateMath.plusDays(from, 14)
        Recurrence.MONTHLY -> DateMath.plusMonths(from, 1)
    }

    /** Due today or overdue - both remain actionable until fulfilled. */
    fun isDue(schedule: Schedule, now: Long): Boolean =
        schedule.active && DateMath.startOfDayMillis(schedule.nextDueDate) <= DateMath.startOfDayMillis(now)

    /**
     * The status of THIS particular scheduled occurrence (today's, if one's due, or whichever is
     * next/overdue) - not an aggregate "health of the schedule over time." Derived purely from
     * nextDueDate vs now, no completion lookup needed: if nextDueDate is in the future, the
     * occurrence that WAS due must already have been attended (that's the only way nextDueDate
     * advances - see advanceAfterCompletion), so "not yet due" reads as DONE/attended.
     */
    fun status(schedule: Schedule, now: Long): ScheduleStatus {
        if (!schedule.active) return ScheduleStatus.DONE
        val dueDay = DateMath.startOfDayMillis(schedule.nextDueDate)
        val today = DateMath.startOfDayMillis(now)
        return when {
            dueDay > today -> ScheduleStatus.DONE
            dueDay == today -> ScheduleStatus.PENDING
            else -> ScheduleStatus.MISSED
        }
    }

    /**
     * How many of this schedule's expected occurrences (from [Schedule.startDate] through today)
     * have no completion recorded at all - distinct from a *late* completion (see
     * ContactDetailViewModel.lateCount), which still has a completion, just not on time.
     *
     * This is a genuine structural limitation worth being explicit about: the data model only
     * ever stores actual completions, never a record of "this specific date was due and nothing
     * happened" - a single very-late completion and several separately-missed occurrences look
     * identical from stored data alone (nextDueDate just keeps waiting until *something* arrives).
     * So this is derived, not directly tracked: expected-occurrence-count minus actual-completion-
     * count. For a DAILY schedule that's a meaningful "how many days had zero call" figure; treat
     * it as an estimate, not a per-occurrence audit trail.
     *
     * Today's own occurrence is the one edge case that needs care: it can't be counted as
     * "expected" (and so potentially missed) the instant its day starts, or every schedule would
     * read as missed before there was ever a chance to fulfill it. For an important, timed
     * schedule it counts once today's actual due time has passed - computed from [now]'s own
     * calendar day, deliberately not [Schedule.nextDueDate] (which this whole calendar-day model
     * is already decoupled from, per the note above - nextDueDate only ever tracks the single next
     * pending occurrence, not "today" specifically). Otherwise it's given the full day, only
     * counting from tomorrow.
     */
    fun missedCount(schedule: Schedule, completionCount: Int, now: Long): Int {
        val elapsedDays = DateMath.daysBetween(schedule.startDate, now)
        if (elapsedDays < 0) return 0

        val todaysDueInstant = DateMath.startOfDayMillis(now) + (schedule.preferredTimeMinutes ?: 0) * 60_000L
        val todaysOccurrenceExpected = schedule.isImportant && schedule.preferredTimeMinutes != null &&
            todaysDueInstant <= now

        val expectedCycles = when (schedule.recurrence) {
            Recurrence.ONE_TIME -> if (elapsedDays == 0L) (if (todaysOccurrenceExpected) 1 else 0) else 1
            Recurrence.DAILY -> elapsedDays.toInt() + (if (todaysOccurrenceExpected) 1 else 0)
            Recurrence.WEEKLY -> {
                val fullWeeksElapsed = (elapsedDays / 7).toInt()
                val currentCycleStartedToday = elapsedDays % 7 == 0L
                fullWeeksElapsed + (if (!currentCycleStartedToday || todaysOccurrenceExpected) 1 else 0)
            }
            Recurrence.BIWEEKLY -> {
                val fullCyclesElapsed = (elapsedDays / 14).toInt()
                val currentCycleStartedToday = elapsedDays % 14 == 0L
                fullCyclesElapsed + (if (!currentCycleStartedToday || todaysOccurrenceExpected) 1 else 0)
            }
            Recurrence.MONTHLY -> {
                val fullMonthsElapsed = DateMath.monthsBetween(schedule.startDate, now)
                val currentCycleStartedToday =
                    DateMath.plusMonths(schedule.startDate, fullMonthsElapsed) == DateMath.startOfDayMillis(now)
                fullMonthsElapsed.toInt() + (if (!currentCycleStartedToday || todaysOccurrenceExpected) 1 else 0)
            }
        }
        return (expectedCycles - completionCount).coerceAtLeast(0)
    }
}
