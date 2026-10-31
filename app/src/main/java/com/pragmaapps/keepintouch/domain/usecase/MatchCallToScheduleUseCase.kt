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
    /**
     * [callerNumber] is the number a notification's caller Person carried in its tel: uri (see
     * CallerNumber), when it had one - compared against schedules' saved numbers by their last 10
     * digits (PhoneNumberMatching). When present it's trusted over the name:
     * - it matches exactly one active schedule -> that schedule, even if the name points elsewhere
     *   or matches nothing (Samsung's incallui shows only a generic "Call" as the name);
     * - the name matches several schedules -> the one whose saved number matches breaks the tie;
     * - the name matches nothing and the number matches several -> they need confirmation.
     * Without a number, matching is name-only exactly as before.
     */
    suspend operator fun invoke(notificationName: String, callerNumber: String? = null): MatchOutcome {
        val numberMatches = callerNumber?.let { findActiveByNumber(it) }.orEmpty()
        if (numberMatches.size == 1) {
            return MatchOutcome.UniqueSchedule(
                numberMatches[0].id,
                MatchConfidence.PHONE_NUMBER,
                "The caller's number (from the notification's caller details) matched exactly one active schedule's saved number."
            )
        }

        // Some sources hand us a raw phone number instead of a name (the OS's own contact-name
        // resolution failed) - name-normalizing a number could never match a schedule's display
        // name anyway, so branch straight to number-matching instead of wasting a name lookup.
        if (PhoneNumberMatching.looksLikePhoneNumber(notificationName)) {
            return evaluateByPhoneNumber(notificationName)
        }
        val normalizedCaller = NameNormalizer.normalize(notificationName)
        val strongCandidates = scheduleRepository.findActiveByNormalizedName(normalizedCaller)

        if (strongCandidates.size >= 2 && numberMatches.isNotEmpty()) {
            val numberIds = numberMatches.map { it.id }.toSet()
            val both = strongCandidates.filter { it.id in numberIds }
            if (both.size == 1) {
                return MatchOutcome.UniqueSchedule(
                    both[0].id,
                    MatchConfidence.PHONE_NUMBER,
                    "Caller name '$normalizedCaller' matches ${strongCandidates.size} active schedules - the caller's number picked the one with that saved number."
                )
            }
        }
        if (strongCandidates.isEmpty() && numberMatches.size >= 2) {
            return MatchOutcome.DuplicateSchedules(
                numberMatches.map { it.id },
                "The caller's name matched no schedule, and the caller's number matches ${numberMatches.size} active schedules with that saved number."
            )
        }
        return evaluateAgainst(notificationName, normalizedCaller, strongCandidates)
    }

    private suspend fun findActiveByNumber(number: String): List<Schedule> =
        scheduleRepository.getActiveSnapshot().filter { schedule ->
            schedule.numberSnapshot?.let { PhoneNumberMatching.matches(it, number) } ?: false
        }

    /**
     * Deliberately narrower than name-matching: only ever compares against already-active
     * schedules, never the broader "any synced contact" fallback name-matching uses for the
     * ContactWithoutSchedule prompt. A bare phone number is weaker signal than a name (easy to
     * misdial, easy to coincidentally share digits with formatting stripped), so it shouldn't be
     * expanding into "want to schedule this contact?" suggestions the way a real name match can.
     */
    private suspend fun evaluateByPhoneNumber(rawNumber: String): MatchOutcome {
        val matches = findActiveByNumber(rawNumber)
        return when {
            matches.size == 1 -> MatchOutcome.UniqueSchedule(
                matches[0].id,
                MatchConfidence.PHONE_NUMBER,
                "The notification showed a phone number, not a name - matched the one active schedule with that saved number."
            )
            matches.size >= 2 -> MatchOutcome.DuplicateSchedules(
                matches.map { it.id },
                "The notification showed a phone number, not a name - it matches ${matches.size} active schedules with that saved number."
            )
            else -> MatchOutcome.NoMatch
        }
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
