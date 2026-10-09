package com.pragmaapps.keepintouch.domain.model

/**
 * A qualifying call that could not be auto-attached to exactly one schedule - two or more active
 * schedules share the caller's exact (normalized) name or saved number.
 * Surfaced on the Confirm screen until a human resolves it.
 */
data class PendingMatch(
    val id: Long,
    val callEventId: Long,
    val candidateScheduleIds: List<Long>,
    val candidateContactIds: List<Long>,
    val reason: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val resolvedScheduleId: Long?
)
