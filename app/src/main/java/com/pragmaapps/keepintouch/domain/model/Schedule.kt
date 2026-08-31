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
    /** Minutes since midnight the call is expected at, e.g. 10:00am = 600. Null = no specific time. */
    val preferredTimeMinutes: Int? = null,
    /**
     * "Must not be missed" - drives a reminder notification 10 minutes before [preferredTimeMinutes]
     * (or before the window start, if [comfortableTimeEndMinutes] is also set). Derived automatically
     * from whether a call time is set (never user-toggled directly) - see ScheduleSheetViewModel.save().
     */
    val isImportant: Boolean = false,
    /**
     * Minutes since midnight the contact is comfortably reachable *until*, e.g. 11:00am = 660. Only
     * meaningful alongside [preferredTimeMinutes] (the window start) - null means this schedule has
     * a single call time rather than an availability window. When set, the reminder message changes
     * from "must not be missed" to "this is a good time to call" - see ImportantCallReminderReceiver.
     */
    val comfortableTimeEndMinutes: Int? = null
)
