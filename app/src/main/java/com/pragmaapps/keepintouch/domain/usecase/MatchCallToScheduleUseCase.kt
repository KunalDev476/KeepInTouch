package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import com.pragmaapps.keepintouch.domain.util.NameSimilarity
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

        val allActive = scheduleRepository.getActiveSnapshot()
        val scored = allActive
            .map { it to NameSimilarity.similarity(normalizedCaller, it.normalizedNameSnapshot) }
            .filter { it.second >= NameSimilarity.MIN_CANDIDATE_THRESHOLD }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) {
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

        val top = scored.first()
        val isUniqueTop = scored.size == 1 || scored[1].second < top.second
        return if (top.second >= NameSimilarity.AUTO_MATCH_THRESHOLD && isUniqueTop) {
            MatchOutcome.UniqueSchedule(
                top.first.id,
                MatchConfidence.FUZZY,
                "Fuzzy match score %.2f (>= %.2f threshold) uniquely identifies this schedule.".format(top.second, NameSimilarity.AUTO_MATCH_THRESHOLD)
            )
        } else {
            MatchOutcome.UncertainSchedules(
                scored.map { it.first.id },
                "Fuzzy match(es) below the confident/unique threshold (top score %.2f); needs human confirmation.".format(top.second)
            )
        }
    }
}
