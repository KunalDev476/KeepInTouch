package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeAll(): Flow<List<Schedule>>
    fun observeActive(): Flow<List<Schedule>>
    fun observeByContactLookupKey(lookupKey: String): Flow<List<Schedule>>
    suspend fun findById(id: Long): Schedule?
    suspend fun findByIds(ids: List<Long>): List<Schedule>
    suspend fun findActiveByNormalizedName(normalizedName: String): List<Schedule>
    suspend fun getActiveSnapshot(): List<Schedule>
    suspend fun findByContactLookupKey(lookupKey: String): List<Schedule>

    /** Any schedule at all - active, paused, or a completed one-time - not just currently-active ones. */
    suspend fun hasAnySchedule(): Boolean

    /** Snapshots the contact's name/number at creation time - see [Schedule] doc for why. */
    suspend fun createSchedule(
        contact: Contact,
        recurrence: Recurrence,
        startDate: Long,
        nextDueDate: Long,
        note: String?,
        preferredTimeMinutes: Int? = null,
        isImportant: Boolean = false,
        comfortableTimeEndMinutes: Int? = null
    ): Long

    suspend fun update(schedule: Schedule)
    suspend fun delete(id: Long)
}
