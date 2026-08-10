package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.DigestReminderTime
import kotlinx.coroutines.flow.Flow

interface DigestReminderRepository {
    /** Seeds the single default 8pm slot on first-ever read if the table is still empty. */
    fun observeAll(): Flow<List<DigestReminderTime>>
    suspend fun getAllSnapshot(): List<DigestReminderTime>
    suspend fun add(hour: Int, minute: Int, label: String): Long
    suspend fun update(reminder: DigestReminderTime)
    suspend fun delete(reminder: DigestReminderTime)
}
