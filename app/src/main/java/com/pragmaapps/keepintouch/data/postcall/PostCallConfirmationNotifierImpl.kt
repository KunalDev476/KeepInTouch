package com.pragmaapps.keepintouch.data.postcall

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.MainActivity
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.reminder.ReminderNotifications
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.PostCallConfirmationNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PostCallConfirmation"

/**
 * Posts "Did you talk to X?" after an outgoing call whose answer couldn't be detected (see
 * UnconfirmedOutgoingCallRule), with Mark complete / Leave scheduled actions handled by
 * [PostCallConfirmationReceiver]. One notification per call event, so two such calls in a row
 * don't overwrite each other's prompt.
 */
@Singleton
class PostCallConfirmationNotifierImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PostCallConfirmationNotifier {

    override fun show(event: CallEvent, schedule: Schedule) {
        try {
            showUnsafe(event, schedule)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to post post-call confirmation for event id=${event.id}", t)
        }
    }

    private fun showUnsafe(event: CallEvent, schedule: Schedule) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            AppLogger.e(TAG, "POST_NOTIFICATIONS isn't granted - post-call confirmation for event id=${event.id} dropped")
            return
        }
        val notificationId = notificationIdFor(event.id)
        val name = schedule.displayNameSnapshot
        val text = "We couldn't tell if $name picked up, so this call didn't count towards the schedule. " +
            "Mark it complete if you talked."

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID_POST_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Did you talk to $name?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Mark complete", actionIntent(PostCallConfirmationReceiver.ACTION_MARK_COMPLETE, event, schedule, notificationId))
            .addAction(0, "Leave scheduled", actionIntent(PostCallConfirmationReceiver.ACTION_LEAVE_SCHEDULED, event, schedule, notificationId))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        AppLogger.i(
            TAG,
            "Asked whether the call with \"$name\" should complete its schedule (event id=${event.id})",
            crashlyticsMessage = "Asked whether event id=${event.id} should complete schedule id=${schedule.id}"
        )
    }

    private fun actionIntent(action: String, event: CallEvent, schedule: Schedule, notificationId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Distinct per call and per action, so neither button's extras overwrite the other's.
            notificationId * 2 + if (action == PostCallConfirmationReceiver.ACTION_MARK_COMPLETE) 0 else 1,
            Intent(context, PostCallConfirmationReceiver::class.java).apply {
                this.action = action
                putExtra(PostCallConfirmationReceiver.EXTRA_CALL_EVENT_ID, event.id)
                putExtra(PostCallConfirmationReceiver.EXTRA_SCHEDULE_ID, schedule.id)
                putExtra(PostCallConfirmationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun notificationIdFor(callEventId: Long): Int = NOTIFICATION_ID_BASE + (callEventId % 100_000).toInt()

    private companion object {
        const val NOTIFICATION_ID_BASE = 1_000_000
    }
}
