package com.pragmaapps.keepintouch.data.reminder

import com.pragmaapps.keepintouch.data.logging.ReminderSkip
import com.pragmaapps.keepintouch.data.logging.Breadcrumbs
import com.pragmaapps.keepintouch.data.logging.Breadcrumb
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.logging.CrashReporter
import com.pragmaapps.keepintouch.data.logging.ExactAlarmNotAllowed
import com.pragmaapps.keepintouch.data.logging.ReminderArmFailed
import com.pragmaapps.keepintouch.data.logging.ReminderCancelFailed
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.PriorityEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderScheduler"

/**
 * Arms/cancels the exact alarm behind a schedule's "must not be missed" reminder (or, when
 * [Schedule.comfortableTimeEndMinutes] is also set, its "good time to call" availability-window
 * reminder), fired 10 minutes before its due time / window start. Uses
 * AlarmManager.setExactAndAllowWhileIdle rather than WorkManager: this
 * needs to fire at a specific wall-clock moment even through Doze, which WorkManager doesn't
 * guarantee. Requires SCHEDULE_EXACT_ALARM (Android 12+); if it's not granted, the reminder is
 * simply not armed - never crashes, never silently retries with inexact timing that would defeat
 * the point of a 10-minutes-before warning.
 *
 * Each schedule only ever arms its OWN alarm here; ImportantCallReminderReceiver is what merges
 * multiple still-outstanding schedules into a single notification when one of these fires.
 */
@Singleton
class ImportantCallReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun reschedule(schedule: Schedule) {
        cancel(schedule.id)
        if (schedule.active && schedule.isImportant && schedule.preferredTimeMinutes != null) {
            arm(schedule)
        } else {
            Breadcrumbs.log(Breadcrumb.REMINDER_SCHEDULED, "armed" to false, "skip" to ReminderSkip.NOT_ELIGIBLE)
            AppLogger.d(
                TAG,
                "Not arming reminder for scheduleId=${schedule.id}: active=${schedule.active}, " +
                    "isImportant=${schedule.isImportant}, hasTime=${schedule.preferredTimeMinutes != null}"
            )
        }
    }

    fun cancel(scheduleId: Long) {
        try {
            alarmManager().cancel(pendingIntentFor(scheduleId))
        } catch (t: Throwable) {
            // Genuinely unexpected - AlarmManager.cancel() on a non-existent PendingIntent is
            // normally a silent no-op, not a throw - worth Crashlytics visibility if it happens.
            AppLogger.e(TAG, "Could not cancel reminder for scheduleId=$scheduleId", t)
            CrashReporter.report(ReminderCancelFailed(t))
        }
    }

    private fun arm(schedule: Schedule) {
        val dueInstant = PriorityEngine.dueInstantMillis(schedule)
        val fireAt = dueInstant - REMINDER_LEAD_MS
        if (fireAt <= System.currentTimeMillis()) {
            Breadcrumbs.log(Breadcrumb.REMINDER_SCHEDULED, "armed" to false, "skip" to ReminderSkip.IN_PAST)
            AppLogger.i(
                TAG,
                "Reminder for scheduleId=${schedule.id} (${schedule.displayNameSnapshot}) would fire in the past " +
                    "(due ${DateMath.formatDate(dueInstant)}, computed fireAt=$fireAt <= now=${System.currentTimeMillis()}) - not armed. " +
                    "Likely cause: due date/time is today but already less than 10 minutes away, or in the past."
            )
            return
        }

        val manager = alarmManager()
        if (!manager.canScheduleExactAlarms()) {
            Breadcrumbs.log(Breadcrumb.REMINDER_SCHEDULED, "armed" to false, "skip" to ReminderSkip.NO_EXACT_ALARM)
            AppLogger.e(
                TAG,
                "SCHEDULE_EXACT_ALARM not granted; reminder for scheduleId=${schedule.id} (${schedule.displayNameSnapshot}) not armed. " +
                    "Grant it via Settings > Apps > this app > Alarms & reminders."
            )
            CrashReporter.report(
                ExactAlarmNotAllowed(schedule.recurrence, schedule.comfortableTimeEndMinutes != null, minutesUntil(dueInstant))
            )
            return
        }

        // No per-schedule payload needed beyond the action - ImportantCallReminderReceiver reads
        // live from the DB on every firing (it needs to merge in whatever else is currently
        // pending, not just this one schedule), so nothing here would ever be read back.
        val intent = Intent(context, ImportantCallReminderReceiver::class.java).apply {
            action = ACTION_REMIND
        }
        try {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                fireAt,
                PendingIntent.getBroadcast(
                    context,
                    schedule.id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            Breadcrumbs.log(
                Breadcrumb.REMINDER_SCHEDULED,
                "armed" to true,
                "recurrence" to schedule.recurrence,
                "minutes_until_fire" to minutesUntil(fireAt)
            )
            AppLogger.i(
                TAG,
                "Armed reminder for scheduleId=${schedule.id} (${schedule.displayNameSnapshot}): fires at $fireAt, " +
                    "10 minutes before due ${DateMath.formatDate(dueInstant)}"
            )
        } catch (t: SecurityException) {
            // canScheduleExactAlarms() said yes above, yet the call still threw - a genuinely
            // surprising, broken state rather than an expected permission gap.
            AppLogger.e(TAG, "Could not schedule exact alarm for scheduleId=${schedule.id}", t)
            CrashReporter.report(
                ReminderArmFailed(schedule.recurrence, schedule.comfortableTimeEndMinutes != null, minutesUntil(dueInstant), t)
            )
        }
    }

    private fun minutesUntil(instant: Long): Long = (instant - System.currentTimeMillis()) / 60_000

    private fun pendingIntentFor(scheduleId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            Intent(context, ImportantCallReminderReceiver::class.java).apply { action = ACTION_REMIND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmManager(): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val REMINDER_LEAD_MS = 10 * 60 * 1000L
        const val ACTION_REMIND = "com.pragmaapps.keepintouch.action.IMPORTANT_CALL_REMINDER"
    }
}
