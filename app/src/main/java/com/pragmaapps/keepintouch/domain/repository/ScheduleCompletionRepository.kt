package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.ScheduleCompletion
import kotlinx.coroutines.flow.Flow

interface ScheduleCompletionRepository {
    fun observeAll(): Flow<List<ScheduleCompletion>>
    fun observeForSchedule(scheduleId: Long): Flow<List<ScheduleCompletion>>

    suspend fun record(
        scheduleId: Long,
        dueDate: Long,
        completedAt: Long,
        callEventId: Long?,
        matchConfidence: MatchConfidence,
        matchMethod: String
    ): Long
}
