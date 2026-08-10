package com.pragmaapps.keepintouch.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.data.logging.AppLogger

private const val TAG = "ReminderNotifications"

/**
 * The two notification channels this app posts to directly (not the call-detection listener,
 * which reads others' notifications, never posts). Kept separate on purpose: channel importance
 * (not the per-notification setPriority, which Android 8+ ignores in favor of the channel) is
 * what actually controls heads-up/sound - a 3x-daily digest reusing the important-call channel's
 * IMPORTANCE_HIGH would pop up as an intrusive heads-up every single time, which defeats the
 * point of a quiet periodic summary.
 */
object ReminderNotifications {
    const val CHANNEL_ID_IMPORTANT = "important_call_reminders"
    const val CHANNEL_ID_DIGEST = "digest_reminders"

    /**
     * Called unconditionally from KeepInTouchApplication.onCreate() on every process start,
     * before anything else runs - an uncaught exception here (a misbehaving OEM
     * NotificationManager) would mean the app never launches at all, so this is wrapped
     * defensively same as every other OEM-controlled framework call in this app. Worst case on
     * failure: reminder notifications silently have no channel to post to (they'd no-op), which
     * is far better than the app not starting.
     */
    fun ensureChannel(context: Context) {
        try {
            ensureChannelUnsafe(context)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to create notification channels - reminder notifications may not appear", t)
        }
    }

    private fun ensureChannelUnsafe(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_IMPORTANT,
                "Important call reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "A heads-up 30 minutes before a call you marked as must-not-miss."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_DIGEST,
                "Pending calls digest",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A daily summary of how many calls (important and normal) are still pending, at times you choose."
            }
        )
    }
}
