package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import kotlinx.coroutines.flow.Flow

interface CallEventRepository {
    fun observeAll(): Flow<List<CallEvent>>
    fun observeForSchedule(scheduleId: Long): Flow<List<CallEvent>>
    suspend fun findByNotifKey(notifKey: String): CallEvent?
    /** The most recently finalized episode for this key, if any - the counterpart of [findByNotifKey]. */
    suspend fun findLatestFinalizedByNotifKey(notifKey: String): CallEvent?
    suspend fun findPostedBetween(fromMs: Long, toMs: Long): List<CallEvent>
    suspend fun findById(id: Long): CallEvent?
    suspend fun upsert(event: CallEvent): Long
    suspend fun update(event: CallEvent)
    suspend fun countByDirection(direction: Direction): Int
    suspend fun clearAll()
}
