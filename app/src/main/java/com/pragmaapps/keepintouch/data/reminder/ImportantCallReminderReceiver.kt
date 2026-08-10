package com.pragmaapps.keepintouch.data.reminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.data.logging.AppLogger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "ImportantCallReminder"

/**
 * Fired by AlarmManager exactly once, 30 minutes before an "important" schedule's due time (see
 * ImportantCallReminderScheduler). All the data needed to post the notification travels in the
 * alarm's Intent extras - no DB read here, so this stays a plain BroadcastReceiver rather than
 * needing Hilt injection.
 *
 * Uses goAsync(): this is exactly the "app was closed/swiped away" wake-up path, so the process
 * may be short-lived once onReceive() returns - without goAsync(), AppLogger's async DB write
 * (and, in principle, the notification post itself) could get killed mid-flight before it lands,
 * which would silently defeat the whole point of logging this specific code path.
 */
class ImportantCallReminderReceiver : BroadcastReceiver() {

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ImportantCallReminderScheduler.ACTION_REMIND) return
        val pendingResult = goAsync()

        try {
            val scheduleId = intent.getLongExtra(ImportantCallReminderScheduler.EXTRA_SCHEDULE_ID, -1L)
            val contactName = intent.getStringExtra(ImportantCallReminderScheduler.EXTRA_CONTACT_NAME)
            val dueInstant = intent.getLongExtra(ImportantCallReminderScheduler.EXTRA_DUE_INSTANT, -1L)

            if (contactName == null) {
                AppLogger.e(TAG, "Alarm fired for scheduleId=$scheduleId but had no contact name extra - dropping")
                return
            }

            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                AppLogger.e(TAG, "Alarm fired for scheduleId=$scheduleId ($contactName) but POST_NOTIFICATIONS isn't granted - reminder silently dropped")
                return
            }

            val timeLabel = if (dueInstant > 0) {
                Instant.ofEpochMilli(dueInstant).atZone(ZoneId.systemDefault()).format(timeFormatter)
            } else {
                "soon"
            }

            val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID_IMPORTANT)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Important call coming up")
                .setContentText("You have an important call with $contactName at $timeLabel")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(scheduleId.toInt(), notification)
            AppLogger.i(TAG, "Posted important-call reminder for scheduleId=$scheduleId ($contactName at $timeLabel)")
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to post important-call reminder", t)
        } finally {
            pendingResult.finish()
        }
    }
}
