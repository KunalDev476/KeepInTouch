package com.pragmaapps.keepintouch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pragmaapps.keepintouch.domain.model.MatchConfidence

@Entity(
    tableName = "schedule_completions",
    indices = [Index(value = ["scheduleId"]), Index(value = ["callEventId"])]
)
data class ScheduleCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val dueDate: Long,
    val completedAt: Long,
    /** Null for a manual completion (Today's swipe-to-mark-done) - there's no detected call to point to. */
    val callEventId: Long?,
    val matchConfidence: MatchConfidence,
    val matchMethod: String
)
