package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.MatchConfidence

/**
 * Pure result of comparing a call's notificationName against ACTIVE schedules (and, if nothing
 * matched, synced contacts). Computed for every finalized call - qualifying or not - so Diagnostics
 * can always explain "why matched / why not," even when no action was taken.
 */
sealed class MatchOutcome {
    /** Caller name did not match any active schedule or any synced contact. */
    data object NoMatch : MatchOutcome()

    /** Exactly one active schedule matched confidently. */
    data class UniqueSchedule(val scheduleId: Long, val confidence: MatchConfidence, val explanation: String) : MatchOutcome()

    /** Zero schedules matched, but a synced contact with no active schedule did. */
    data class ContactWithoutSchedule(val contactIds: List<Long>, val explanation: String) : MatchOutcome()

    /** Two or more active schedules share this (normalized) name - needs human confirmation. */
    data class DuplicateSchedules(val scheduleIds: List<Long>, val explanation: String) : MatchOutcome()
}
