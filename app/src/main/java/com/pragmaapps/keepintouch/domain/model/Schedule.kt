package com.pragmaapps.keepintouch.domain.model

/**
 * A recurring "stay in touch with this person" reminder. Contact identity is snapshotted at
 * creation time (name, number) so later address-book edits can't silently break matching.
 *
 * [normalizedNameSnapshot] is a pragmatic addition beyond the literal spec field list: matching
 * (comparing a call notification's name against schedules) requires a normalized snapshot to
 * compare against, and there is no other field this could derive from at match time without
 * re-reading the live contact - which is exactly what snapshotting is meant to avoid.
 */
data class Schedule(
    val id: Long,
    val contactLookupKey: String,
    val displayNameSnapshot: String,
    val normalizedNameSnapshot: String,
    val numberSnapshot: String?,
    val recurrence: Recurrence,
    val startDate: Long,
    val nextDueDate: Long,
    val active: Boolean,
    val note: String?,
    val createdAt: Long,
    val importance: Importance = Importance.MEDIUM,
    /** Minutes since midnight the call is expected at, e.g. 10:00am = 600. Null = no specific time. */
    val preferredTimeMinutes: Int? = null,
    /** "Must not be missed" - drives a reminder notification 30 minutes before [preferredTimeMinutes]. */
    val isImportant: Boolean = false
)
