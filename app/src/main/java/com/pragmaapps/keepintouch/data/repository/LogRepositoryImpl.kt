package com.pragmaapps.keepintouch.data.repository

import com.pragmaapps.keepintouch.data.db.dao.LogEntryDao
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.domain.model.LogEntry
import com.pragmaapps.keepintouch.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepositoryImpl @Inject constructor(
    private val dao: LogEntryDao
) : LogRepository {

    override fun observeRecent(limit: Int): Flow<List<LogEntry>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun clearAll() = dao.clearAll()
}
