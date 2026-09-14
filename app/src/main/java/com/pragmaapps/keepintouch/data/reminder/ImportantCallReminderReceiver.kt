package com.pragmaapps.keepintouch.data.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.MainActivity
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.PriorityEngine
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "ImportantCallReminder"
private const val NOTIFICATION_ID = 999_002

/**
 * Fired by AlarmManager, once per important schedule, 10 minutes before its due time / window
 * start (still one exact alarm armed per schedule - see ImportantCallReminderScheduler). Rather
 * than posting a standalone notification for just the schedule whose alarm happened to fire,
 * every firing re-reads live from the DB (needs @AndroidEntryPoint + goAsync(), same reasoning as
 * DigestReminderReceiver - the whole point is to reflect what's ACTUALLY still pending right now,
 * not what was true when this alarm was armed) and merges in every important schedule whose own
 * 10-minutes-before threshold has already passed and that still hasn't been fulfilled.
 *
 * That's what makes an earlier still-outstanding reminder (say, 2:00) carry forward into a later
 * one (3:00's alarm) as a single combined notification, while a schedule that DID get fulfilled in
 * the meantime naturally drops out - fulfilling advances its nextDueDate into the future, so
 * RecurrenceEngine.isDue stops matching it (see ProcessFinalizedCallUseCase.tryFulfill).
 *
 * One fixed notification ID: each new firing replaces the previous merged notification instead of
 * stacking a duplicate alongside it.
 */
@AndroidEntryPoint
class ImportantCallReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleRepository: ScheduleRepository

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ImportantCallReminderScheduler.ACTION_REMIND) return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val due = scheduleRepository.getActiveSnapshot()
                    .filter { schedule -> isReminderPending(schedule, now) }
                    .sortedBy { PriorityEngine.dueInstantMillis(it) }

                if (due.isEmpty()) {
                    AppLogger.d(TAG, "Reminder alarm fired but nothing is currently pending a reminder - not posting")
                    return@launch
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    AppLogger.e(TAG, "Reminder fired with ${due.size} schedule(s) pending but POST_NOTIFICATIONS isn't granted - dropped")
                    return@launch
                }

                postReminder(context, due)
                AppLogger.i(TAG, "Posted reminder for ${due.size} schedule(s): ${due.joinToString { it.displayNameSnapshot }}")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to post important-call reminder", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** This schedule's own 10-minutes-before threshold has been reached, and it's still unfulfilled - regardless of whether ITS alarm is what fired just now. */
    private fun isReminderPending(schedule: Schedule, now: Long): Boolean =
        schedule.isImportant && schedule.preferredTimeMinutes != null &&
            RecurrenceEngine.isDue(schedule, now) &&
            PriorityEngine.dueInstantMillis(schedule) - ImportantCallReminderScheduler.REMINDER_LEAD_MS <= now

    private fun postReminder(context: Context, due: List<Schedule>) {
        val (title, text) = if (due.size == 1) {
            singleReminderText(due[0])
        } else {
            "Calls coming up" to due.joinToString(", ") { schedule -> schedule.displayNameSnapshot + timeSuffix(schedule) }
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID_IMPORTANT)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun singleReminderText(schedule: Schedule): Pair<String, String> {
        val timeLabel = formatTime(PriorityEngine.dueInstantMillis(schedule))
        val windowEnd = PriorityEngine.windowEndInstantMillis(schedule)
        return if (windowEnd != null) {
            "Good time to call" to
                "${schedule.displayNameSnapshot} is usually free from $timeLabel to ${formatTime(windowEnd)} - this is a great time to call."
        } else {
            "Important call coming up" to "You have an important call with ${schedule.displayNameSnapshot} at $timeLabel"
        }
    }

    private fun timeSuffix(schedule: Schedule): String {
        val start = formatTime(PriorityEngine.dueInstantMillis(schedule))
        val windowEnd = PriorityEngine.windowEndInstantMillis(schedule)
        return if (windowEnd != null) " ($start-${formatTime(windowEnd)})" else " ($start)"
    }

    private fun formatTime(instant: Long): String =
        Instant.ofEpochMilli(instant).atZone(ZoneId.systemDefault()).format(timeFormatter)
}
