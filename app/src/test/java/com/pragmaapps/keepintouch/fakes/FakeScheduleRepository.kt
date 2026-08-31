package com.pragmaapps.keepintouch.fakes

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory fake for pure unit tests - no Room, no Android framework. */
class FakeScheduleRepository(initial: List<Schedule> = emptyList()) : ScheduleRepository {
    private val state = MutableStateFlow(initial)
    val schedules: StateFlow<List<Schedule>> = state

    override fun observeAll() = state
    override fun observeActive() = MutableStateFlow(state.value.filter { it.active })
    override fun observeByContactLookupKey(lookupKey: String) =
        MutableStateFlow(state.value.filter { it.contactLookupKey == lookupKey })

    override suspend fun findById(id: Long): Schedule? = state.value.find { it.id == id }
    override suspend fun findByIds(ids: List<Long>): List<Schedule> = state.value.filter { it.id in ids }
    override suspend fun findActiveByNormalizedName(normalizedName: String): List<Schedule> =
        state.value.filter { it.active && it.normalizedNameSnapshot == normalizedName }

    override suspend fun getActiveSnapshot(): List<Schedule> = state.value.filter { it.active }
    override suspend fun findByContactLookupKey(lookupKey: String): List<Schedule> =
        state.value.filter { it.contactLookupKey == lookupKey }

    override suspend fun hasAnySchedule(): Boolean = state.value.isNotEmpty()

    override suspend fun createSchedule(
        contact: Contact,
        recurrence: Recurrence,
        startDate: Long,
        nextDueDate: Long,
        note: String?,
        preferredTimeMinutes: Int?,
        isImportant: Boolean,
        comfortableTimeEndMinutes: Int?
    ): Long {
        val id = (state.value.maxOfOrNull { it.id } ?: 0) + 1
        state.value = state.value + Schedule(
            id = id,
            contactLookupKey = contact.lookupKey,
            displayNameSnapshot = contact.displayName,
            normalizedNameSnapshot = contact.normalizedName,
            numberSnapshot = contact.primaryNumber,
            recurrence = recurrence,
            startDate = startDate,
            nextDueDate = nextDueDate,
            active = true,
            note = note,
            createdAt = 0,
            preferredTimeMinutes = preferredTimeMinutes,
            isImportant = isImportant,
            comfortableTimeEndMinutes = comfortableTimeEndMinutes
        )
        return id
    }

    override suspend fun update(schedule: Schedule) {
        state.value = state.value.map { if (it.id == schedule.id) schedule else it }
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}
