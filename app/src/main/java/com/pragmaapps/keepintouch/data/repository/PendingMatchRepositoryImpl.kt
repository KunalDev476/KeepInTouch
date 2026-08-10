package com.pragmaapps.keepintouch.data.repository

import com.pragmaapps.keepintouch.data.db.dao.PendingMatchDao
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.data.db.mapper.toEntity
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingMatchRepositoryImpl @Inject constructor(
    private val dao: PendingMatchDao
) : PendingMatchRepository {

    override fun observeAll(): Flow<List<PendingMatch>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeUnresolved(): Flow<List<PendingMatch>> = dao.observeUnresolved().map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: Long): PendingMatch? = dao.findById(id)?.toDomain()

    override suspend fun findByCallEventId(callEventId: Long): PendingMatch? = dao.findByCallEventId(callEventId)?.toDomain()

    override suspend fun insert(pendingMatch: PendingMatch): Long = dao.insert(pendingMatch.toEntity())

    override suspend fun update(pendingMatch: PendingMatch) = dao.update(pendingMatch.toEntity())
}
