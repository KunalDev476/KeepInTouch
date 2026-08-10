package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.util.DetectionSettings
import com.pragmaapps.keepintouch.domain.util.QualifyingCallRule
import javax.inject.Inject

/** Builds the "why matched / why not" explanation for every captured call, for the Diagnostics screen. */
class CallEventDiagnostics @Inject constructor() {

    data class Display(
        val event: CallEvent,
        val matchedScheduleName: String?,
        val disqualifyReasons: List<String>,
        val pendingReason: String?
    )

    fun buildDisplays(
        events: List<CallEvent>,
        schedules: List<Schedule>,
        settings: DetectionSettings,
        pendingMatches: List<PendingMatch> = emptyList()
    ): List<Display> {
        val scheduleNameById = schedules.associate { it.id to it.displayNameSnapshot }
        val pendingByCallEventId = pendingMatches.associateBy { it.callEventId }

        return events.map { event ->
            val reasons = if (event.qualified) {
                emptyList()
            } else {
                QualifyingCallRule.disqualifyReasons(
                    QualifyingCallRule.Signals(
                        connected = event.connected,
                        direction = event.direction,
                        isGroupCall = event.isGroupCall,
                        isVideo = event.isVideo,
                        inferredDurationMs = event.inferredDurationMs
                    ),
                    settings
                )
            }
            val pendingReason = if (event.matchedScheduleId == null) pendingByCallEventId[event.id]?.reason else null

            Display(
                event = event,
                matchedScheduleName = event.matchedScheduleId?.let { scheduleNameById[it] },
                disqualifyReasons = reasons,
                pendingReason = pendingReason
            )
        }
    }
}
