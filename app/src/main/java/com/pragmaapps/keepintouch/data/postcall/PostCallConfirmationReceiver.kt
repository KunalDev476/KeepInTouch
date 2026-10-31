package com.pragmaapps.keepintouch.data.postcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.usecase.FulfillScheduleUseCase
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "PostCallConfirmation"

/**
 * Handles the two buttons on the post-call confirmation (see PostCallConfirmationNotifierImpl).
 * Mark complete re-reads the schedule first - it may have been completed by hand, deleted, or
 * already advanced in the meantime, and a stale tap must never complete it twice.
 */
@AndroidEntryPoint
class PostCallConfirmationReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject lateinit var callEventRepository: CallEventRepository
    @Inject lateinit var fulfillSchedule: FulfillScheduleUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_MARK_COMPLETE && action != ACTION_LEAVE_SCHEDULED) return
        val callEventId = intent.getLongExtra(EXTRA_CALL_EVENT_ID, -1)
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1))

        if (action == ACTION_LEAVE_SCHEDULED) {
            AppLogger.i(TAG, "Left schedule id=$scheduleId as scheduled after event id=$callEventId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val schedule = scheduleRepository.findById(scheduleId)
                val now = System.currentTimeMillis()
                when {
                    schedule == null -> AppLogger.i(TAG, "Mark complete tapped but schedule id=$scheduleId no longer exists")
                    !schedule.active || !RecurrenceEngine.isDue(schedule, now) ->
                        AppLogger.i(TAG, "Mark complete tapped but schedule id=$scheduleId is no longer due - nothing to do")
                    else -> {
                        val callEnd = callEventRepository.findById(callEventId)?.endTime
                        fulfillSchedule(
                            schedule = schedule,
                            callEventId = callEventId.takeIf { it >= 0 },
                            confidence = MatchConfidence.CONFIRMED_BY_USER,
                            matchMethod = "Confirmed from the post-call notification",
                            completedAt = callEnd ?: now
                        )
                        AppLogger.i(TAG, "Schedule id=$scheduleId completed from the post-call notification (event id=$callEventId)")
                    }
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to complete schedule id=$scheduleId from the post-call notification", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_COMPLETE = "com.pragmaapps.keepintouch.action.POST_CALL_MARK_COMPLETE"
        const val ACTION_LEAVE_SCHEDULED = "com.pragmaapps.keepintouch.action.POST_CALL_LEAVE_SCHEDULED"
        const val EXTRA_CALL_EVENT_ID = "callEventId"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
