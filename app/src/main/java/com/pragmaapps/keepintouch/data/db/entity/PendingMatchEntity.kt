package com.pragmaapps.keepintouch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [resolvedAt]/[resolvedScheduleId] are pragmatic additions beyond the literal field list: without
 * them the Confirm screen has no way to know an item was already handled, and there is nowhere
 * else to record which candidate the user picked.
 */
@Entity(
    tableName = "pending_matches",
    indices = [Index(value = ["callEventId"], unique = true)]
)
data class PendingMatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callEventId: Long,
    /** Comma-separated Schedule ids this call could plausibly belong to (empty = "matches a contact, no schedule yet"). */
    val candidateScheduleIds: String,
    /** Comma-separated Contact ids offered when zero schedules matched but a synced contact's name did. */
    val candidateContactIds: String = "",
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val resolvedScheduleId: Long? = null
)
