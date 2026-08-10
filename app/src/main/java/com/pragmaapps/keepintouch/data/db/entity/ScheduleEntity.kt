package com.pragmaapps.keepintouch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pragmaapps.keepintouch.domain.model.Importance
import com.pragmaapps.keepintouch.domain.model.Recurrence

@Entity(
    tableName = "schedules",
    indices = [Index(value = ["contactLookupKey"]), Index(value = ["normalizedNameSnapshot"])]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactLookupKey: String,
    val displayNameSnapshot: String,
    val normalizedNameSnapshot: String,
    val numberSnapshot: String?,
    val recurrence: Recurrence,
    val startDate: Long,
    val nextDueDate: Long,
    val active: Boolean = true,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val importance: Importance = Importance.MEDIUM,
    val preferredTimeMinutes: Int? = null,
    val isImportant: Boolean = false
)
