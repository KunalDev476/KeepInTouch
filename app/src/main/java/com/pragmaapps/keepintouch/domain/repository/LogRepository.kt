package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.LogEntry
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun observeRecent(limit: Int = 500): Flow<List<LogEntry>>
    suspend fun clearAll()
}
