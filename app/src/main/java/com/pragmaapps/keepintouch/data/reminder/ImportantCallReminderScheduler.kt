package com.pragmaapps.keepintouch.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.PriorityEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderScheduler"

/**
 * Arms/cancels the exact alarm behind a schedule's "must not be missed" reminder, fired 30 minutes
 * before its due time. Uses AlarmManager.setExactAndAllowWhileIdle rather than WorkManager: this
 * needs to fire at a specific wall-clock moment even through Doze, which WorkManager doesn't
 * guarantee. Requires SCHEDULE_EXACT_ALARM (Android 12+); if it's not granted, the reminder is
 * simply not armed - never crashes, never silently retries with inexact timing that would defeat
 * the point of a 30-minutes-before warning.
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
            AppLogger.nonFatal(TAG, "Could not cancel reminder for scheduleId=$scheduleId", t)
        }
    }

    private fun arm(schedule: Schedule) {
        val dueInstant = PriorityEngine.dueInstantMillis(schedule)
        val fireAt = dueInstant - REMINDER_LEAD_MS
        if (fireAt <= System.currentTimeMillis()) {
            AppLogger.i(
                TAG,
                "Reminder for scheduleId=${schedule.id} (${schedule.displayNameSnapshot}) would fire in the past " +
                    "(due ${DateMath.formatDate(dueInstant)}, computed fireAt=$fireAt <= now=${System.currentTimeMillis()}) - not armed. " +
                    "Likely cause: due date/time is today but already less than 30 minutes away, or in the past.",
                crashlyticsMessage = "Reminder for scheduleId=${schedule.id} would fire in the past " +
                    "(due ${DateMath.formatDate(dueInstant)}, computed fireAt=$fireAt <= now=${System.currentTimeMillis()}) - not armed."
            )
            return
        }

        val manager = alarmManager()
        if (!manager.canScheduleExactAlarms()) {
            AppLogger.e(
                TAG,
                "SCHEDULE_EXACT_ALARM not granted; reminder for scheduleId=${schedule.id} (${schedule.displayNameSnapshot}) not armed. " +
                    "Grant it via Settings > Apps > this app > Alarms & reminders.",
                crashlyticsMessage = "SCHEDULE_EXACT_ALARM not granted; reminder for scheduleId=${schedule.id} not armed."
            )
            return
        }

        val intent = Intent(context, ImportantCallReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
            putExtra(EXTRA_CONTACT_NAME, schedule.displayNameSnapshot)
            putExtra(EXTRA_DUE_INSTANT, dueInstant)
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
            AppLogger.i(
                TAG,
                "Armed reminder for scheduleId=${schedule.id} (${schedule.displayNameSnapshot}): fires at $fireAt, " +
                    "30 minutes before due ${DateMath.formatDate(dueInstant)}",
                crashlyticsMessage = "Armed reminder for scheduleId=${schedule.id}: fires at $fireAt, " +
                    "30 minutes before due ${DateMath.formatDate(dueInstant)}"
            )
        } catch (t: SecurityException) {
            // canScheduleExactAlarms() said yes above, yet the call still threw - a genuinely
            // surprising, broken state worth Crashlytics visibility, not an expected permission gap.
            AppLogger.nonFatal(TAG, "Could not schedule exact alarm for scheduleId=${schedule.id}", t)
        }
    }

    private fun pendingIntentFor(scheduleId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            Intent(context, ImportantCallReminderReceiver::class.java).apply { action = ACTION_REMIND },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmManager(): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val REMINDER_LEAD_MS = 30 * 60 * 1000L
        const val ACTION_REMIND = "com.pragmaapps.keepintouch.action.IMPORTANT_CALL_REMINDER"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_DUE_INSTANT = "extra_due_instant"
    }
}
