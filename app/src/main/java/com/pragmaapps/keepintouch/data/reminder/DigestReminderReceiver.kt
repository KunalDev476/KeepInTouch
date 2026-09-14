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
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DigestReminder"
private const val DIGEST_NOTIFICATION_ID = 999_001
private const val MAX_NAMES_LISTED = 5

/**
 * Fired by AlarmManager once per enabled digest slot (see DigestReminderScheduler). Unlike
 * ImportantCallReminderReceiver, this needs live DB access (it has to compute what's pending
 * *right now*, not carry pre-computed data in the Intent), so it's @AndroidEntryPoint + goAsync()
 * rather than a plain receiver.
 */
@AndroidEntryPoint
class DigestReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleRepository: ScheduleRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DigestReminderScheduler.ACTION_DIGEST_REMINDER) return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val pending = scheduleRepository.getActiveSnapshot().filter { RecurrenceEngine.isDue(it, now) }

                if (pending.isEmpty()) {
                    AppLogger.d(TAG, "Digest fired - nothing pending, not posting")
                    return@launch
                }

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    AppLogger.e(TAG, "Digest fired with ${pending.size} pending but POST_NOTIFICATIONS isn't granted - silently dropped")
                    return@launch
                }

                postDigest(context, pending)
                AppLogger.i(TAG, "Posted digest: ${pending.size} pending (${pending.count { it.isImportant }} important)")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to post digest reminder", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postDigest(context: Context, pending: List<Schedule>) {
        val important = pending.filter { it.isImportant }
        val normal = pending.filterNot { it.isImportant }
        val title = buildTitle(important.size, normal.size)

        val orderedNames = (important + normal).map { it.displayNameSnapshot }
        val inboxStyle = NotificationCompat.InboxStyle().setBigContentTitle(title)
        orderedNames.take(MAX_NAMES_LISTED).forEach { inboxStyle.addLine(it) }
        if (orderedNames.size > MAX_NAMES_LISTED) {
            inboxStyle.setSummaryText("+${orderedNames.size - MAX_NAMES_LISTED} more")
        }

        val preview = orderedNames.take(3).joinToString(", ") + if (orderedNames.size > 3) "…" else ""

        val contentIntent = PendingIntent.getActivity(
            context,
            DIGEST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID_DIGEST)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        // One constant ID: a later digest firing replaces the previous one instead of stacking.
        NotificationManagerCompat.from(context).notify(DIGEST_NOTIFICATION_ID, notification)
    }

    private fun buildTitle(importantCount: Int, normalCount: Int): String {
        val total = importantCount + normalCount
        return when {
            total == 1 && importantCount == 1 -> "1 important call is pending"
            total == 1 -> "1 call is pending"
            importantCount > 0 -> "$total calls are pending ($importantCount important)"
            else -> "$total calls are pending"
        }
    }
}
