package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Schedule
import javax.inject.Inject

/** Today's swipe-right "mark done" action - no detected call, so callEventId is null by design. */
class ManuallyCompleteScheduleUseCase @Inject constructor(
    private val fulfillSchedule: FulfillScheduleUseCase
) {
    suspend operator fun invoke(schedule: Schedule) {
        fulfillSchedule(
            schedule = schedule,
            callEventId = null,
            confidence = MatchConfidence.CONFIRMED_BY_USER,
            matchMethod = "Manually marked done from Today"
        )
    }
}
