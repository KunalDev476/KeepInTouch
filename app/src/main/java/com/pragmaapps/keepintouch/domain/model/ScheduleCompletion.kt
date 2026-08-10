package com.pragmaapps.keepintouch.domain.model

/**
 * A single fulfillment record: this schedule's due occurrence on [dueDate] was satisfied.
 * [callEventId] is null for a manual completion (Today's swipe-to-mark-done) - there's no
 * detected call to point to in that case.
 */
data class ScheduleCompletion(
    val id: Long,
    val scheduleId: Long,
    val dueDate: Long,
    val completedAt: Long,
    val callEventId: Long?,
    val matchConfidence: MatchConfidence,
    val matchMethod: String
)
