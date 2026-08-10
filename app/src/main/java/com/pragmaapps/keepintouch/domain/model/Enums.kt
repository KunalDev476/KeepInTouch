package com.pragmaapps.keepintouch.domain.model

/** How often a schedule recurs once fulfilled. */
enum class Recurrence {
    ONE_TIME,
    DAILY,
    WEEKLY
}

/**
 * User-set weight on how much a schedule's lateness should matter, applied as a multiplier over
 * the computed lateness score in [com.pragmaapps.keepintouch.domain.util.PriorityEngine] - not a
 * standalone ranking. Mandatory per schedule (defaults to MEDIUM, always shown at creation).
 */
enum class Importance(val multiplier: Double) {
    LOW(0.5),
    MEDIUM(1.0),
    HIGH(2.0)
}

/** A schedule's health right now, relative to [Schedule.nextDueDate] - see RecurrenceEngine.status(). */
enum class ScheduleStatus {
    /** Not active, or the current cycle isn't due yet - the previous one must have been completed to get here. */
    DONE,
    /** Due today, not yet completed. */
    PENDING,
    /** Due date has passed without completion. */
    MISSED
}

/** Severity for the in-app App Logs screen (Settings -> Diagnostics -> App Logs). */
enum class LogLevel {
    DEBUG,
    INFO,
    ERROR
}

/** Direction inferred for a call episode. UNKNOWN when it can't be determined from what was observed. */
enum class Direction {
    INCOMING,
    OUTGOING,
    UNKNOWN
}

/** How confident the app is that a CallEvent belongs to a given Schedule/Contact. */
enum class MatchConfidence {
    /** Exact, case-sensitive match against the raw display name snapshot. */
    EXACT,
    /** Match after name normalization (case/whitespace/diacritics/emoji-insensitive). */
    NORMALIZED,
    /** Confident fuzzy string match, unique among candidates. */
    FUZZY,
    /**
     * The notification never carried a name at all - just a raw phone number (the OS's own
     * contact-name resolution failed) - matched against an active schedule's saved number
     * instead. See PhoneNumberMatching.
     */
    PHONE_NUMBER,
    /** Ambiguity was resolved by an explicit human choice on the Confirm screen. */
    CONFIRMED_BY_USER
}
