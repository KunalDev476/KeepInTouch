package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import javax.inject.Inject

/**
 * Records a completion and advances recurrence. Shared by auto-match, Confirm-screen resolution,
 * and manual mark-done. When [avatarHashToLearn] is provided (a confidently-matched call has one),
 * it's learned onto the matched contact for future disambiguation - never seeded from the
 * address-book photo, only ever from a past call notification's own icon.
 */
class FulfillScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val completionRepository: ScheduleCompletionRepository,
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(
        schedule: Schedule,
        callEventId: Long?,
        confidence: MatchConfidence,
        matchMethod: String,
        avatarHashToLearn: String? = null,
        completedAt: Long = System.currentTimeMillis()
    ) {
        completionRepository.record(
            scheduleId = schedule.id,
            dueDate = schedule.nextDueDate,
            completedAt = completedAt,
            callEventId = callEventId,
            matchConfidence = confidence,
            matchMethod = matchMethod
        )
        scheduleRepository.update(RecurrenceEngine.advanceAfterCompletion(schedule, completedAt))
        AppAnalytics.logScheduleFulfilled(schedule.recurrence.name, confidence.name, matchMethod)

        if (avatarHashToLearn != null) {
            contactRepository.findByLookupKey(schedule.contactLookupKey)?.let { contact ->
                contactRepository.learnAvatarHash(contact.id, avatarHashToLearn)
            }
        }
    }
}
