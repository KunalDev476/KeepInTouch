package com.pragmaapps.keepintouch.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.repository.DigestReminderRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BootReminderResched"

/**
 * No AlarmManager alarm - exact or inexact-repeating - survives a reboot; re-arm both reminder
 * systems: every still-eligible important-call alarm, and every enabled digest slot.
 */
@AndroidEntryPoint
class BootReminderRescheduler : BroadcastReceiver() {

    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var reminderScheduler: ImportantCallReminderScheduler
    @Inject lateinit var digestReminderRepository: DigestReminderRepository
    @Inject lateinit var digestReminderScheduler: DigestReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val eligible = scheduleRepository.getActiveSnapshot()
                    .filter { it.isImportant && it.preferredTimeMinutes != null }
                eligible.forEach { reminderScheduler.reschedule(it) }
                AppLogger.i(TAG, "Re-armed ${eligible.size} important-call reminder(s) after boot")

                val digestSlots = digestReminderRepository.getAllSnapshot()
                digestReminderScheduler.rescheduleAll(digestSlots)
                AppLogger.i(TAG, "Re-armed ${digestSlots.count { it.enabled }} digest reminder slot(s) after boot")
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to re-arm reminders after boot", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
