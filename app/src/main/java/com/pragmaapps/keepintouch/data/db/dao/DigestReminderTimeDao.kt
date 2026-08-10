package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pragmaapps.keepintouch.data.db.entity.DigestReminderTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DigestReminderTimeDao {
    @Query("SELECT * FROM digest_reminder_times ORDER BY hour, minute")
    fun observeAll(): Flow<List<DigestReminderTimeEntity>>

    @Query("SELECT * FROM digest_reminder_times")
    suspend fun getAllSnapshot(): List<DigestReminderTimeEntity>

    @Query("SELECT COUNT(*) FROM digest_reminder_times")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: DigestReminderTimeEntity): Long

    @Update
    suspend fun update(entity: DigestReminderTimeEntity)

    @Delete
    suspend fun delete(entity: DigestReminderTimeEntity)
}
