package com.pragmaapps.keepintouch.data.repository

import com.pragmaapps.keepintouch.data.db.dao.CallEventDao
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.data.db.mapper.toEntity
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallEventRepositoryImpl @Inject constructor(
    private val dao: CallEventDao
) : CallEventRepository {

    override fun observeAll(): Flow<List<CallEvent>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForSchedule(scheduleId: Long): Flow<List<CallEvent>> =
        dao.observeForSchedule(scheduleId).map { list -> list.map { it.toDomain() } }

    override suspend fun findByNotifKey(notifKey: String): CallEvent? = dao.findByNotifKey(notifKey)?.toDomain()

    override suspend fun findById(id: Long): CallEvent? = dao.findById(id)?.toDomain()

    override suspend fun upsert(event: CallEvent): Long = dao.upsert(event.toEntity())

    override suspend fun update(event: CallEvent) = dao.update(event.toEntity())

    override suspend fun countByDirection(direction: Direction): Int = dao.countByDirection(direction)
}
