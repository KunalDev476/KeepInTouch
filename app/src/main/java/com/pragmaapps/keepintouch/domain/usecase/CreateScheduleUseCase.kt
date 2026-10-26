package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import javax.inject.Inject

/**
 * Creates a brand-new schedule - shared by the Create Schedule sheet and voice scheduling, so both
 * enforce the same rules.
 *
 * Two active schedules for two DIFFERENT contacts can never share a normalized name: a call
 * notification only carries a name, so future calls for either person would be unmatchable (see
 * MatchCallToScheduleUseCase). Several schedules for the SAME contact are fine, e.g. a weekly
 * Saturday one plus a weekly Sunday one - the matcher resolves those to whichever one is due.
 */
class CreateScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    sealed class Result {
        data class Created(val scheduleId: Long) : Result()
        data class NameCollision(val collidingWith: Schedule) : Result()
    }

    suspend operator fun invoke(
        contact: Contact,
        recurrence: Recurrence,
        nextDueDate: Long,
        note: String?,
        preferredTimeMinutes: Int?,
        comfortableTimeEndMinutes: Int?
    ): Result {
        val normalizedName = NameNormalizer.normalize(contact.displayName)
        val colliding = scheduleRepository.findActiveByNormalizedName(normalizedName)
            .filter { it.contactLookupKey != contact.lookupKey }
        if (colliding.isNotEmpty()) return Result.NameCollision(colliding.first())

        val id = scheduleRepository.createSchedule(
            contact = contact,
            recurrence = recurrence,
            startDate = nextDueDate,
            nextDueDate = nextDueDate,
            note = note,
            preferredTimeMinutes = preferredTimeMinutes,
            // "Must not be missed" is derived from having a call time - never a separate toggle.
            isImportant = preferredTimeMinutes != null,
            comfortableTimeEndMinutes = if (preferredTimeMinutes != null) comfortableTimeEndMinutes else null
        )
        return Result.Created(id)
    }
}
