package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import com.pragmaapps.keepintouch.domain.util.PhoneNumberMatching
import javax.inject.Inject

/**
 * Matches a call's notificationName against active schedules (spec section 4). Read-only - never
 * writes anything - so it can be reused both for taking action and for a Diagnostics explanation.
 */
class MatchCallToScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val contactRepository: ContactRepository
) {
    suspend operator fun invoke(notificationName: String): MatchOutcome {
        // Some sources hand us a raw phone number instead of a name (the OS's own contact-name
        // resolution failed) - name-normalizing a number could never match a schedule's display
        // name anyway, so branch straight to number-matching instead of wasting a name lookup.
        if (PhoneNumberMatching.looksLikePhoneNumber(notificationName)) {
            return evaluateByPhoneNumber(notificationName)
        }
        val normalizedCaller = NameNormalizer.normalize(notificationName)
        val strongCandidates = scheduleRepository.findActiveByNormalizedName(normalizedCaller)
        return evaluateAgainst(notificationName, normalizedCaller, strongCandidates)
    }

    /**
     * Deliberately narrower than name-matching: only ever compares against already-active
     * schedules, never the broader "any synced contact" fallback name-matching uses for the
     * ContactWithoutSchedule prompt. A bare phone number is weaker signal than a name (easy to
     * misdial, easy to coincidentally share digits with formatting stripped), so it shouldn't be
     * expanding into "want to schedule this contact?" suggestions the way a real name match can.
     */
    private suspend fun evaluateByPhoneNumber(rawNumber: String): MatchOutcome {
        val activeSchedules = scheduleRepository.getActiveSnapshot()
        val matches = activeSchedules.filter { schedule ->
            schedule.numberSnapshot?.let { PhoneNumberMatching.matches(it, rawNumber) } ?: false
        }
        val sameContact = if (matches.size >= 2) pickForSingleContact(matches) else null
        return when {
            matches.size == 1 -> MatchOutcome.UniqueSchedule(
                matches[0].id,
                MatchConfidence.PHONE_NUMBER,
                "The notification showed a phone number, not a name - matched the one active schedule with that saved number."
            )
            sameContact != null -> MatchOutcome.UniqueSchedule(
                sameContact.id,
                MatchConfidence.PHONE_NUMBER,
                "The notification showed a phone number, not a name - it matches ${matches.size} active schedules, " +
                    "all for the same contact; picked the one due soonest."
            )
            matches.size >= 2 -> MatchOutcome.DuplicateSchedules(
                matches.map { it.id },
                "The notification showed a phone number, not a name - it matches ${matches.size} active schedules with that saved number."
            )
            else -> MatchOutcome.NoMatch
        }
    }

    /**
     * Several schedules for ONE contact (e.g. a weekly Saturday one and a weekly Sunday one) aren't
     * an ambiguity worth asking about - it's the same person either way. The call goes to the one
     * due soonest: an overdue/today one if there is one (the caller then fulfils it only if it's
     * actually due - see ProcessFinalizedCallUseCase.tryFulfill), so a mid-week call never
     * completes next Saturday's schedule early. Null when the schedules span different contacts,
     * which still needs a human to pick.
     */
    private fun pickForSingleContact(schedules: List<Schedule>): Schedule? {
        if (schedules.map { it.contactLookupKey }.distinct().size != 1) return null
        return schedules.minByOrNull { it.nextDueDate }
    }

    private suspend fun evaluateAgainst(
        rawCallerName: String,
        normalizedCaller: String,
        strongCandidates: List<Schedule>
    ): MatchOutcome {
        if (strongCandidates.size == 1) {
            val schedule = strongCandidates[0]
            val confidence = if (schedule.displayNameSnapshot == rawCallerName) MatchConfidence.EXACT else MatchConfidence.NORMALIZED
            return MatchOutcome.UniqueSchedule(
                schedule.id,
                confidence,
                "Normalized caller name '$normalizedCaller' matched exactly one active schedule (${confidence.name})."
            )
        }
        if (strongCandidates.size >= 2) {
            pickForSingleContact(strongCandidates)?.let { schedule ->
                val confidence = if (schedule.displayNameSnapshot == rawCallerName) MatchConfidence.EXACT else MatchConfidence.NORMALIZED
                return MatchOutcome.UniqueSchedule(
                    schedule.id,
                    confidence,
                    "Normalized caller name '$normalizedCaller' matches ${strongCandidates.size} active schedules, all for " +
                        "the same contact - picked the one due soonest (${confidence.name})."
                )
            }
            return MatchOutcome.DuplicateSchedules(
                strongCandidates.map { it.id },
                "Normalized caller name '$normalizedCaller' matches ${strongCandidates.size} active schedules with the same name."
            )
        }

        // Exact (normalized) name matches only - no similarity/fuzzy tier. A near-miss like
        // "Amar" vs "Amar Sir", or "Abhinav Maheshwari" vs "Dhruv Maheshwari", is simply not a
        // match: a wrongly auto-completed schedule is worse than one the user marks done by hand.

        // "Want to schedule this contact?" only makes sense once the user has shown some
        // intent to use scheduling at all - with zero schedules ever created, this would
        // otherwise fire a confirm prompt for literally every call from any saved contact.
        if (!scheduleRepository.hasAnySchedule()) return MatchOutcome.NoMatch

        val contactMatches = contactRepository.findByNormalizedName(normalizedCaller)
        val withoutSchedule = contactMatches.filter { contact ->
            scheduleRepository.findByContactLookupKey(contact.lookupKey).none { it.active }
        }
        return if (withoutSchedule.isNotEmpty()) {
            MatchOutcome.ContactWithoutSchedule(
                withoutSchedule.map { it.id },
                "Caller name matches ${withoutSchedule.size} synced contact(s) with no active schedule."
            )
        } else {
            MatchOutcome.NoMatch
        }
    }
}
