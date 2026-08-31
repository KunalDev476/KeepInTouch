package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
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
    private val pendingMatchRepository: PendingMatchRepository
) {
    suspend operator fun invoke(event: CallEvent) {
        val outcome = matchCallToSchedule(event.notificationName)
        AppLogger.i(TAG, "Match outcome for \"${event.notificationName}\": ${outcome::class.simpleName}")

        // Always record the best-effort match attempt, even when not qualified, purely for Diagnostics.
        if (outcome is MatchOutcome.UniqueSchedule) {
            callEventRepository.update(event.copy(matchedScheduleId = outcome.scheduleId, matchConfidence = outcome.confidence))
        }

        if (!event.qualified) {
            AppLogger.d(TAG, "\"${event.notificationName}\" matched but not qualified - not acting on it")
            return // logged only - never act on a non-qualifying call
        }

        when (outcome) {
            is MatchOutcome.UniqueSchedule -> tryFulfill(event, outcome)
            // The call matched a synced contact with no active schedule at all - no schedule to
            // confirm against, so there's nothing to ask the user. Diagnostics/Call Logs can still
            // explain this outcome (see outcome.explanation); it just never becomes a Confirm prompt.
            is MatchOutcome.ContactWithoutSchedule -> Unit
            is MatchOutcome.DuplicateSchedules -> insertPending(event, outcome.scheduleIds, outcome.explanation)
            is MatchOutcome.UncertainSchedules -> insertPending(event, outcome.scheduleIds, outcome.explanation)
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

    private suspend fun tryFulfill(event: CallEvent, outcome: MatchOutcome.UniqueSchedule) {
        val schedule = scheduleRepository.findById(outcome.scheduleId) ?: return
        if (!RecurrenceEngine.isDue(schedule, System.currentTimeMillis())) return // matched, but not currently due - logged only
        fulfillSchedule(schedule, event.id, outcome.confidence, outcome.explanation, avatarHashToLearn = event.avatarHash)
    }
}
