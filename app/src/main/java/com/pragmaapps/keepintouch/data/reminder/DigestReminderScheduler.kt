package com.pragmaapps.keepintouch.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.DigestReminderTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DigestReminderScheduler"

/**
 * Arms/cancels the daily repeating alarm behind each collective "N calls pending" digest slot.
 * Deliberately inexact (AlarmManager.setInexactRepeating), unlike ImportantCallReminderScheduler's
 * exact alarm: a digest firing a few minutes late doesn't matter the way a 30-minutes-before-a-
 * specific-call warning does, and inexact repeating needs no SCHEDULE_EXACT_ALARM permission at
 * all - one less thing that can silently fail to be granted.
 */
@Singleton
class DigestReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun rescheduleAll(reminders: List<DigestReminderTime>) {
        reminders.forEach { reminder ->
            cancel(reminder.id)
            if (reminder.enabled) arm(reminder)
        }
    }

    fun cancel(reminderId: Long) {
        try {
            alarmManager().cancel(pendingIntentFor(reminderId))
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Could not cancel digest reminder id=$reminderId", t)
        }
    }

    private fun arm(reminder: DigestReminderTime) {
        val triggerAt = nextTriggerMillis(reminder.hour, reminder.minute)
        try {
            alarmManager().setInexactRepeating(
                AlarmManager.RTC,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pendingIntentFor(reminder.id)
            )
            AppLogger.i(TAG, "Armed digest reminder id=${reminder.id} (${reminder.label}) - next fires ~$triggerAt, then daily")
        } catch (t: SecurityException) {
            AppLogger.e(TAG, "Could not arm digest reminder id=${reminder.id}", t)
        }
    }

    /** Today at hour:minute if that's still in the future, otherwise tomorrow at hour:minute. */
    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var candidate = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(hour, minute))
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun pendingIntentFor(reminderId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Offset well clear of ImportantCallReminderScheduler's schedule.id-based request codes.
            (DIGEST_REQUEST_CODE_OFFSET + reminderId).toInt(),
            Intent(context, DigestReminderReceiver::class.java).apply { action = ACTION_DIGEST_REMINDER },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarmManager(): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val ACTION_DIGEST_REMINDER = "com.pragmaapps.keepintouch.action.DIGEST_REMINDER"
        private const val DIGEST_REQUEST_CODE_OFFSET = 100_000L
    }
}
