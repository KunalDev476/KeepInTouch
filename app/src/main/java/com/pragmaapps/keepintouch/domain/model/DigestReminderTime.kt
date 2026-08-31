package com.pragmaapps.keepintouch.domain.model

/**
 * One daily time-of-day slot at which a collective "N calls pending" digest notification fires -
 * see [com.pragmaapps.keepintouch.data.reminder.DigestReminderScheduler]. Covers both normal and
 * "Important" schedules; Important schedules additionally get their own precise 10-minutes-before
 * alarm (see ImportantCallReminderScheduler) - the two systems are independent, not exclusive.
 */
data class DigestReminderTime(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean
)
