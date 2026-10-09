package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import javax.inject.Inject

/**
 * The Confirm screen's "this is the one" / "none of these" action for duplicate-name (or
 * duplicate-number) pending matches. Picking a schedule fulfills it with
 * CONFIRMED_BY_USER; picking none just marks the item resolved with no fulfillment.
 */
class ResolvePendingMatchUseCase @Inject constructor(
    private val pendingMatchRepository: PendingMatchRepository,
    private val scheduleRepository: ScheduleRepository,
    private val callEventRepository: CallEventRepository,
    private val fulfillSchedule: FulfillScheduleUseCase
) {
    suspend operator fun invoke(pendingMatchId: Long, chosenScheduleId: Long?) {
        val pending = pendingMatchRepository.findById(pendingMatchId) ?: return

        if (chosenScheduleId != null) {
            val schedule = scheduleRepository.findById(chosenScheduleId)
            val callEvent = callEventRepository.findById(pending.callEventId)
            if (schedule != null && callEvent != null) {
                fulfillSchedule(
                    schedule = schedule,
                    callEventId = callEvent.id,
                    confidence = MatchConfidence.CONFIRMED_BY_USER,
                    matchMethod = "User resolved ambiguous match ('${pending.reason}') to this contact.",
                    avatarHashToLearn = callEvent.avatarHash
                )
                callEventRepository.update(
                    callEvent.copy(matchedScheduleId = schedule.id, matchConfidence = MatchConfidence.CONFIRMED_BY_USER)
                )
            }
        }

        pendingMatchRepository.update(
            pending.copy(resolvedAt = System.currentTimeMillis(), resolvedScheduleId = chosenScheduleId)
        )
    }
}
