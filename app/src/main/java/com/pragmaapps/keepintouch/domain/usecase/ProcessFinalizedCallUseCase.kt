package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.notification.CallNotificationExtras
import com.pragmaapps.keepintouch.data.prefs.DetectionPreferences
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.PostCallConfirmationNotifier
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import com.pragmaapps.keepintouch.domain.util.UnconfirmedOutgoingCallRule
import javax.inject.Inject

private const val TAG = "ProcessFinalizedCall"

/**
 * Implements spec section 4 (match call -> schedule) and section 5 (fulfill), operating purely
 * through repository interfaces. Matching (attaching matchedScheduleId/matchConfidence for
 * Diagnostics) runs for every finalized call. Taking action - auto-fulfilling, or raising a
 * Confirm-screen prompt - only happens for QUALIFYING calls: a non-qualifying call can never
 * fulfill anything, so asking "which contact was this?" about one would be noise.
 */
class ProcessFinalizedCallUseCase @Inject constructor(
    private val matchCallToSchedule: MatchCallToScheduleUseCase,
    private val fulfillSchedule: FulfillScheduleUseCase,
    private val scheduleRepository: ScheduleRepository,
    private val callEventRepository: CallEventRepository,
    private val pendingMatchRepository: PendingMatchRepository,
    private val syncContacts: SyncContactsUseCase,
    private val detectionPreferences: DetectionPreferences,
    private val postCallConfirmationNotifier: PostCallConfirmationNotifier
) {
    suspend operator fun invoke(event: CallEvent) {
        // The caller's number, when the dialer's notification carried one in its caller Person -
        // trusted over the name (see MatchCallToScheduleUseCase).
        val callerNumber = CallNotificationExtras.lastCallerNumber(event.rawJson)
        var outcome = matchCallToSchedule(event.notificationName, callerNumber)
        // The contact may have been renamed moments ago, before the contacts observer's sync
        // refreshed the schedule's name snapshot - sync once and retry before giving up. Only for
        // qualifying calls, since only those can act on a match.
        if (event.qualified && (outcome == MatchOutcome.NoMatch || outcome is MatchOutcome.ContactWithoutSchedule)) {
            syncContacts()
            outcome = matchCallToSchedule(event.notificationName, callerNumber)
        }
        val how = (outcome as? MatchOutcome.UniqueSchedule)?.let { " via ${it.confidence}" }.orEmpty()
        // The number never goes to Crashlytics - only whether there was one.
        AppLogger.i(
            TAG,
            "Match outcome for \"${event.notificationName}\" (number=${callerNumber ?: "none"}): ${outcome::class.simpleName}$how",
            crashlyticsMessage = "Match outcome for event id=${event.id} (hasNumber=${callerNumber != null}): ${outcome::class.simpleName}$how"
        )

        // Always record the best-effort match attempt, even when not qualified, purely for Diagnostics.
        if (outcome is MatchOutcome.UniqueSchedule) {
            callEventRepository.update(event.copy(matchedScheduleId = outcome.scheduleId, matchConfidence = outcome.confidence))
        }

        if (!event.qualified) {
            AppLogger.d(
                TAG,
                "\"${event.notificationName}\" matched but not qualified - not acting on it",
                crashlyticsMessage = "Event id=${event.id} matched but not qualified - not acting on it"
            )
            handleUnconfirmedOutgoing(event, outcome)
            return // never act on a non-qualifying call - except an unconfirmed outgoing one, above
        }

        when (outcome) {
            is MatchOutcome.UniqueSchedule -> tryFulfill(event, outcome)
            // The call matched a synced contact with no active schedule at all - no schedule to
            // confirm against, so there's nothing to ask the user. Diagnostics/Call Logs can still
            // explain this outcome (see outcome.explanation); it just never becomes a Confirm prompt.
            is MatchOutcome.ContactWithoutSchedule -> Unit
            is MatchOutcome.DuplicateSchedules -> insertPending(event, outcome.scheduleIds, outcome.explanation)
            MatchOutcome.NoMatch -> Unit // caller not in contacts / no plausible candidate at all
        }
    }

    private suspend fun insertPending(event: CallEvent, scheduleIds: List<Long>, reason: String) {
        pendingMatchRepository.insert(
            PendingMatch(
                id = 0,
                callEventId = event.id,
                candidateScheduleIds = scheduleIds,
                candidateContactIds = emptyList(),
                reason = reason,
                createdAt = System.currentTimeMillis(),
                resolvedAt = null,
                resolvedScheduleId = null
            )
        )
    }

    /**
     * An outgoing dialer call whose answer couldn't be detected (see UnconfirmedOutgoingCallRule):
     * under 30s the user is asked with a post-call notification, 30s or more completes the schedule.
     * Only a single, currently-due schedule - several matches are already a Confirm-screen prompt.
     */
    private suspend fun handleUnconfirmedOutgoing(event: CallEvent, outcome: MatchOutcome) {
        val decision = UnconfirmedOutgoingCallRule.decide(event, detectionPreferences.settings.value.countOutgoing)
        if (decision == UnconfirmedOutgoingCallRule.Decision.NONE) return
        if (outcome !is MatchOutcome.UniqueSchedule) return
        val schedule = scheduleRepository.findById(outcome.scheduleId) ?: return
        if (!RecurrenceEngine.isDue(schedule, System.currentTimeMillis())) {
            AppLogger.d(TAG, "Unconfirmed outgoing call (event id=${event.id}) matched a schedule that isn't due - nothing to do")
            return
        }
        val durationSec = UnconfirmedOutgoingCallRule.dialToHangUpMs(event.statesSeen)?.div(1000)
        when (decision) {
            UnconfirmedOutgoingCallRule.Decision.ASK_USER -> postCallConfirmationNotifier.show(event, schedule)
            UnconfirmedOutgoingCallRule.Decision.AUTO_COMPLETE -> {
                fulfillSchedule(
                    schedule,
                    event.id,
                    outcome.confidence,
                    "Outgoing call of ${durationSec}s with no answer signal - 30s or longer counts as completed",
                    completedAt = event.endTime ?: System.currentTimeMillis()
                )
                AppLogger.i(TAG, "Unconfirmed outgoing call (event id=${event.id}, ${durationSec}s) completed schedule id=${schedule.id}")
            }
            UnconfirmedOutgoingCallRule.Decision.NONE -> Unit
        }
    }

    private suspend fun tryFulfill(event: CallEvent, outcome: MatchOutcome.UniqueSchedule) {
        val schedule = scheduleRepository.findById(outcome.scheduleId) ?: return
        if (!RecurrenceEngine.isDue(schedule, System.currentTimeMillis())) return // matched, but not currently due - logged only
        fulfillSchedule(schedule, event.id, outcome.confidence, outcome.explanation, avatarHashToLearn = event.avatarHash)
    }
}
