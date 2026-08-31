package com.pragmaapps.keepintouch.data.repository

import com.pragmaapps.keepintouch.data.db.dao.ScheduleCompletionDao
import com.pragmaapps.keepintouch.data.db.entity.ScheduleCompletionEntity
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.ScheduleCompletion
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleCompletionRepositoryImpl @Inject constructor(
    private val dao: ScheduleCompletionDao
) : ScheduleCompletionRepository {

    override fun observeAll(): Flow<List<ScheduleCompletion>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForSchedule(scheduleId: Long): Flow<List<ScheduleCompletion>> =
        dao.observeForSchedule(scheduleId).map { list -> list.map { it.toDomain() } }

    override fun observeForSchedules(scheduleIds: List<Long>): Flow<List<ScheduleCompletion>> =
        if (scheduleIds.isEmpty()) flowOf(emptyList()) else
            dao.observeForSchedules(scheduleIds).map { list -> list.map { it.toDomain() } }

    override suspend fun record(
        scheduleId: Long,
        dueDate: Long,
        completedAt: Long,
        callEventId: Long?,
        matchConfidence: MatchConfidence,
        matchMethod: String
    ): Long = dao.insert(
        ScheduleCompletionEntity(
            scheduleId = scheduleId,
            dueDate = dueDate,
            completedAt = completedAt,
            callEventId = callEventId,
            matchConfidence = matchConfidence,
            matchMethod = matchMethod
        )
    )
}
