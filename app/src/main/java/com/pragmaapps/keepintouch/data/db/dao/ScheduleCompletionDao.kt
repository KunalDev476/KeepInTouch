package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pragmaapps.keepintouch.data.db.entity.ScheduleCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleCompletionDao {
    @Query("SELECT * FROM schedule_completions ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<ScheduleCompletionEntity>>

    @Query("SELECT * FROM schedule_completions WHERE scheduleId = :scheduleId ORDER BY completedAt DESC")
    fun observeForSchedule(scheduleId: Long): Flow<List<ScheduleCompletionEntity>>

    @Query("SELECT * FROM schedule_completions WHERE scheduleId IN (:scheduleIds) ORDER BY completedAt DESC")
    fun observeForSchedules(scheduleIds: List<Long>): Flow<List<ScheduleCompletionEntity>>

    @Query("SELECT * FROM schedule_completions WHERE scheduleId = :scheduleId ORDER BY completedAt DESC")
    suspend fun getForScheduleSnapshot(scheduleId: Long): List<ScheduleCompletionEntity>

    @Insert
    suspend fun insert(completion: ScheduleCompletionEntity): Long

    /** No FK/CASCADE on scheduleId (see ScheduleCompletionEntity) - callers must clean this up explicitly on schedule deletion. */
    @Query("DELETE FROM schedule_completions WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)
}
