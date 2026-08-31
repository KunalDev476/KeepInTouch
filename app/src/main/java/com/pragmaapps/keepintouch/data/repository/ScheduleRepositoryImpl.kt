package com.pragmaapps.keepintouch.data.repository

import com.pragmaapps.keepintouch.data.db.dao.CallEventDao
import com.pragmaapps.keepintouch.data.db.dao.PendingMatchDao
import com.pragmaapps.keepintouch.data.db.dao.ScheduleCompletionDao
import com.pragmaapps.keepintouch.data.db.dao.ScheduleDao
import com.pragmaapps.keepintouch.data.db.entity.ScheduleEntity
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.data.db.mapper.toEntity
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.reminder.ImportantCallReminderScheduler
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val scheduleDao: ScheduleDao,
    private val pendingMatchDao: PendingMatchDao,
    private val completionDao: ScheduleCompletionDao,
    private val callEventDao: CallEventDao,
    private val reminderScheduler: ImportantCallReminderScheduler
) : ScheduleRepository {

    override fun observeAll(): Flow<List<Schedule>> = scheduleDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Schedule>> = scheduleDao.observeActive().map { list -> list.map { it.toDomain() } }

    override fun observeByContactLookupKey(lookupKey: String): Flow<List<Schedule>> =
        scheduleDao.observeByContactLookupKey(lookupKey).map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): Schedule? = scheduleDao.findById(id)?.toDomain()

    override suspend fun findByIds(ids: List<Long>): List<Schedule> =
        if (ids.isEmpty()) emptyList() else scheduleDao.findByIds(ids).map { it.toDomain() }

    override suspend fun findActiveByNormalizedName(normalizedName: String): List<Schedule> =
        scheduleDao.findActiveByNormalizedName(normalizedName).map { it.toDomain() }

    override suspend fun getActiveSnapshot(): List<Schedule> = scheduleDao.getActiveSnapshot().map { it.toDomain() }

    override suspend fun findByContactLookupKey(lookupKey: String): List<Schedule> =
        scheduleDao.findByContactLookupKey(lookupKey).map { it.toDomain() }

    override suspend fun hasAnySchedule(): Boolean = scheduleDao.hasAny()

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
        val entity = ScheduleEntity(
            contactLookupKey = contact.lookupKey,
            displayNameSnapshot = contact.displayName,
            normalizedNameSnapshot = NameNormalizer.normalize(contact.displayName),
            numberSnapshot = contact.primaryNumber,
            recurrence = recurrence,
            startDate = startDate,
            nextDueDate = nextDueDate,
            note = note,
            preferredTimeMinutes = preferredTimeMinutes,
            isImportant = isImportant,
            comfortableTimeEndMinutes = comfortableTimeEndMinutes
        )
        val id = scheduleDao.insert(entity)
        resolveContactOnlyPendingMatches(contact.id, id)
        reminderScheduler.reschedule(entity.copy(id = id).toDomain())
        AppAnalytics.logScheduleCreated(recurrence.name, isImportant)
        return id
    }

    override suspend fun update(schedule: Schedule) {
        scheduleDao.update(schedule.toEntity())
        reminderScheduler.reschedule(schedule)
    }

    override suspend fun delete(id: Long) {
        val recurrence = scheduleDao.findById(id)?.recurrence
        completionDao.deleteForSchedule(id)
        callEventDao.clearScheduleMatch(id)
        scheduleDao.delete(id)
        reminderScheduler.cancel(id)
        recurrence?.let { AppAnalytics.logScheduleDeleted(it.name) }
    }

    /**
     * A "this contact has no schedule yet" prompt (Confirm screen, case b) becomes moot once a
     * schedule exists. We mark it resolved - informational only, no retroactive fulfillment of
     * the call that raised it.
     */
    private suspend fun resolveContactOnlyPendingMatches(contactId: Long, newScheduleId: Long) {
        val unresolved = pendingMatchDao.getUnresolvedSnapshot()
        for (pending in unresolved) {
            val candidateContactIds = pending.candidateContactIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            if (pending.candidateScheduleIds.isBlank() && candidateContactIds.contains(contactId)) {
                pendingMatchDao.update(
                    pending.copy(resolvedAt = System.currentTimeMillis(), resolvedScheduleId = newScheduleId)
                )
            }
        }
    }
}
