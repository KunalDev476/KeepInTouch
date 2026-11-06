package com.pragmaapps.keepintouch.data.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ExactAlarmPermission"

/**
 * ImportantCallReminderScheduler skips arming a reminder while exact alarms aren't allowed, and
 * Android deletes armed ones when the permission is revoked, so nothing is armed until something
 * re-arms it. Android sends this only on a grant, which makes this that moment, the same way
 * BootReminderRescheduler is for a reboot. Digest reminders are inexact and don't need it.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var reminderScheduler: ImportantCallReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val eligible = scheduleRepository.getActiveSnapshot()
                    .filter { it.isImportant && it.preferredTimeMinutes != null }
                eligible.forEach { reminderScheduler.reschedule(it) }
                AppLogger.i(TAG, "Re-armed ${eligible.size} important-call reminder(s) after exact alarms were allowed")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to re-arm reminders after exact alarms were allowed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
