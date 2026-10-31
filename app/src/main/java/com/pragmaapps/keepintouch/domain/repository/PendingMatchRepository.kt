package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.PendingMatch
import kotlinx.coroutines.flow.Flow

interface PendingMatchRepository {
    fun observeAll(): Flow<List<PendingMatch>>
    fun observeUnresolved(): Flow<List<PendingMatch>>
    suspend fun findById(id: Long): PendingMatch?
    suspend fun findByCallEventId(callEventId: Long): PendingMatch?
    suspend fun insert(pendingMatch: PendingMatch): Long
    suspend fun update(pendingMatch: PendingMatch)
    suspend fun clearAll()
}
